package com.ecommerce.inventory.service;

import com.ecommerce.inventory.entity.InventoryItem;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.event.*;
import com.ecommerce.inventory.exception.ResourceNotFoundException;
import com.ecommerce.inventory.kafka.InventoryEventProducer;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final InventoryEventProducer eventProducer;

    @Transactional
    public void processOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null || event.orderId() <= 0) {
            log.error("Discarding invalid OrderCreatedEvent: missing or invalid orderId");
            return;
        }

        if (event.items() == null || event.items().isEmpty()) {
            log.error("Discarding OrderCreatedEvent for orderId={}: items list is empty", event.orderId());
            return;
        }

        // Validate individual item payloads
        for (OrderCreatedEvent.OrderItemPayload item : event.items()) {
            if (item.productId() == null || item.productId() <= 0 || item.quantity() == null || item.quantity() <= 0) {
                log.error("Discarding OrderCreatedEvent for orderId={}: invalid item payload with productId={} and quantity={}",
                        event.orderId(), item.productId(), item.quantity());
                return;
            }
        }

        log.info("Processing stock reservation for orderId={}, itemsCount={}", event.orderId(), event.items().size());

        // Application-level idempotency check
        if (reservationRepository.existsByOrderId(event.orderId())) {
            log.warn("Order {} has already been evaluated for inventory reservation. Discarding duplicate event.", event.orderId());
            return;
        }

        String correlationId = (event.correlationId() != null && !event.correlationId().isBlank())
                ? event.correlationId()
                : (event.eventId() != null ? event.eventId() : UUID.randomUUID().toString());

        // Sort items by productId ascending to guarantee deterministic lock acquisition order and prevent deadlocks
        List<OrderCreatedEvent.OrderItemPayload> sortedItems = event.items().stream()
                .sorted(Comparator.comparing(OrderCreatedEvent.OrderItemPayload::productId))
                .toList();

        try {
            // Phase 1: Pessimistic lock and stock availability check for ALL items
            Map<Long, InventoryItem> lockedItemsMap = new LinkedHashMap<>();
            for (OrderCreatedEvent.OrderItemPayload item : sortedItems) {
                Optional<InventoryItem> invOpt = inventoryRepository.findByProductIdWithPessimisticLock(item.productId());
                if (invOpt.isEmpty() || !invOpt.get().hasSufficientStock(item.quantity())) {
                    String reason = String.format("Insufficient stock for product ID %d (available: %d, requested: %d)",
                            item.productId(),
                            invOpt.map(InventoryItem::getAvailableQuantity).orElse(0),
                            item.quantity());
                    log.warn("Order {} inventory check failed: {}", event.orderId(), reason);

                    // Record rejected status to guarantee idempotency on future duplicate events
                    StockReservation rejectedRecord = StockReservation.builder()
                            .orderId(event.orderId())
                            .productId(item.productId())
                            .quantity(item.quantity())
                            .status(ReservationStatus.REJECTED)
                            .failureReason(reason)
                            .build();
                    reservationRepository.save(rejectedRecord);

                    InventoryRejectedEvent rejectedEvent = new InventoryRejectedEvent(
                            UUID.randomUUID().toString(),
                            correlationId,
                            "INVENTORY_REJECTED",
                            event.orderId(),
                            reason,
                            Instant.now()
                    );
                    eventProducer.sendInventoryRejected(rejectedEvent);
                    return;
                }
                lockedItemsMap.put(item.productId(), invOpt.get());
            }

            // Phase 2: Reserve stock for all items atomically
            List<InventoryReservedEvent.ReservedItemPayload> reservedItems = new ArrayList<>();
            for (OrderCreatedEvent.OrderItemPayload item : sortedItems) {
                InventoryItem inv = lockedItemsMap.get(item.productId());
                inv.reserve(item.quantity());
                inventoryRepository.save(inv);

                StockReservation reservation = StockReservation.builder()
                        .orderId(event.orderId())
                        .productId(item.productId())
                        .quantity(item.quantity())
                        .status(ReservationStatus.RESERVED)
                        .build();
                reservationRepository.save(reservation);

                reservedItems.add(new InventoryReservedEvent.ReservedItemPayload(item.productId(), item.quantity()));
                log.info("Stock reserved for orderId={}, productId={}, quantity={}", event.orderId(), item.productId(), item.quantity());
            }

            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                    UUID.randomUUID().toString(),
                    correlationId,
                    "INVENTORY_RESERVED",
                    event.orderId(),
                    event.customerId(),
                    event.customerEmail(),
                    event.totalAmount(),
                    event.paymentSimulateMode(),
                    reservedItems,
                    Instant.now()
            );
            eventProducer.sendInventoryReserved(reservedEvent);

        } catch (DataIntegrityViolationException e) {
            // Caught when concurrent threads attempt to insert for the same (order_id, product_id)
            log.warn("Concurrent duplicate reservation detected for orderId={} via database unique constraint. Discarding duplicate: {}",
                    event.orderId(), e.getMessage());
        }
    }

    @Transactional
    public void processPaymentFailedCompensation(PaymentFailedEvent event) {
        if (event == null || event.orderId() == null) {
            log.error("Discarding invalid PaymentFailedEvent: missing orderId");
            return;
        }

        log.warn("Executing inventory compensation rollback for failed payment on orderId={}", event.orderId());

        List<StockReservation> reservations = reservationRepository.findByOrderId(event.orderId());
        if (reservations.isEmpty()) {
            log.warn("No stock reservations found to release for orderId={}", event.orderId());
            return;
        }

        List<StockReservation> activeReservations = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .toList();

        if (activeReservations.isEmpty()) {
            log.info("All reservations for orderId={} are already released, fulfilled, or rejected. Skipping duplicate compensation.",
                    event.orderId());
            return;
        }

        String correlationId = (event.correlationId() != null && !event.correlationId().isBlank())
                ? event.correlationId()
                : (event.eventId() != null ? event.eventId() : UUID.randomUUID().toString());

        List<InventoryReservedEvent.ReservedItemPayload> releasedItems = new ArrayList<>();

        for (StockReservation reservation : activeReservations) {
            inventoryRepository.findByProductIdWithPessimisticLock(reservation.getProductId()).ifPresent(inv -> {
                inv.release(reservation.getQuantity());
                inventoryRepository.save(inv);
                log.info("Restored stock for productId={}: added {} back to available (now available={}, reserved={})",
                        reservation.getProductId(), reservation.getQuantity(), inv.getAvailableQuantity(), inv.getReservedQuantity());
            });

            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
            releasedItems.add(new InventoryReservedEvent.ReservedItemPayload(reservation.getProductId(), reservation.getQuantity()));
        }

        if (!releasedItems.isEmpty()) {
            InventoryReleasedEvent releasedEvent = new InventoryReleasedEvent(
                    UUID.randomUUID().toString(),
                    correlationId,
                    "INVENTORY_RELEASED",
                    event.orderId(),
                    releasedItems,
                    Instant.now()
            );
            eventProducer.sendInventoryReleased(releasedEvent);
            log.info("Compensation completed: InventoryReleasedEvent published for orderId={}", event.orderId());
        }
    }

    @Transactional
    public void processPaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null || event.orderId() == null) {
            log.error("Discarding invalid PaymentSucceededEvent: missing orderId");
            return;
        }

        log.info("Payment confirmed for orderId={}. Fulfilling stock reservations permanently.", event.orderId());

        List<StockReservation> reservations = reservationRepository.findByOrderId(event.orderId());
        for (StockReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                inventoryRepository.findByProductIdWithPessimisticLock(reservation.getProductId()).ifPresent(inv -> {
                    inv.fulfill(reservation.getQuantity());
                    inventoryRepository.save(inv);
                    log.info("Stock fulfilled for productId={}: reserved decremented by {}", reservation.getProductId(), reservation.getQuantity());
                });
                reservation.setStatus(ReservationStatus.FULFILLED);
                reservationRepository.save(reservation);
            }
        }
    }

    public Optional<InventoryItem> getStockByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    public List<InventoryItem> getAllStock() {
        return inventoryRepository.findAllByOrderByProductIdAsc();
    }

    /**
     * Replenishes stock for a given product ID using pessimistic write locking.
     * Atomically increases availableQuantity while preserving reservedQuantity and versioning.
     */
    @Transactional
    public InventoryItem replenishStock(Long productId, int quantity) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Replenishment quantity must be greater than zero, got: " + quantity);
        }
        if (quantity > 10000) {
            throw new IllegalArgumentException("Replenishment quantity cannot exceed 10,000 per request, got: " + quantity);
        }

        log.info("Acquiring pessimistic lock to replenish productId={} by {} units", productId, quantity);
        InventoryItem item = inventoryRepository.findByProductIdWithPessimisticLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product ID " + productId + " not found in inventory"));

        int previousAvailable = item.getAvailableQuantity() != null ? item.getAvailableQuantity() : 0;
        int newAvailable = previousAvailable + quantity;
        item.setAvailableQuantity(newAvailable);
        // reservedQuantity is strictly preserved

        InventoryItem saved = inventoryRepository.save(item);
        log.info("Replenishment successful for productId={}: available changed from {} to {}, reserved remains {}",
                productId, previousAvailable, newAvailable, saved.getReservedQuantity());
        return saved;
    }
}
