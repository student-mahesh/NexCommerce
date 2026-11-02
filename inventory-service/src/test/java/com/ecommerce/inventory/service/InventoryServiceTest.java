package com.ecommerce.inventory.service;

import com.ecommerce.inventory.entity.InventoryItem;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.event.*;
import com.ecommerce.inventory.exception.ResourceNotFoundException;
import com.ecommerce.inventory.kafka.InventoryEventProducer;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private InventoryEventProducer eventProducer;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryItem testItem1;
    private InventoryItem testItem2;

    @BeforeEach
    void setUp() {
        testItem1 = InventoryItem.builder()
                .id(1L)
                .productId(10L)
                .sku("TEST-SKU-10")
                .availableQuantity(20)
                .reservedQuantity(0)
                .build();

        testItem2 = InventoryItem.builder()
                .id(2L)
                .productId(20L)
                .sku("TEST-SKU-20")
                .availableQuantity(15)
                .reservedQuantity(0)
                .build();
    }

    @Test
    @DisplayName("Should successfully reserve stock when sufficient quantity is available")
    void shouldReserveStockSuccessfully() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-100", "corr-100", "ORDER_CREATED", 1001L, 1L, "cust@test.com",
                new BigDecimal("100.00"), "SUCCESS",
                List.of(new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 2, new BigDecimal("50.00"))),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(1001L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));

        inventoryService.processOrderCreated(event);

        assertThat(testItem1.getAvailableQuantity()).isEqualTo(18);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(2);
        verify(reservationRepository, times(1)).save(any(StockReservation.class));

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(eventProducer, times(1)).sendInventoryReserved(captor.capture());
        InventoryReservedEvent captured = captor.getValue();
        assertThat(captured.orderId()).isEqualTo(1001L);
        assertThat(captured.correlationId()).isEqualTo("corr-100");
        assertThat(captured.reservedItems()).hasSize(1);
        assertThat(captured.reservedItems().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should reserve multiple products in a single order with correct calculations")
    void shouldReserveMultipleProductsSuccessfully() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-multi", "corr-multi", "ORDER_CREATED", 1005L, 1L, "cust@test.com",
                new BigDecimal("250.00"), "SUCCESS",
                List.of(
                        new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 4, new BigDecimal("50.00")),
                        new OrderCreatedEvent.OrderItemPayload(20L, "TEST-SKU-20", 3, new BigDecimal("50.00"))
                ),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(1005L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));
        when(inventoryRepository.findByProductIdWithPessimisticLock(20L)).thenReturn(Optional.of(testItem2));

        inventoryService.processOrderCreated(event);

        assertThat(testItem1.getAvailableQuantity()).isEqualTo(16);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(4);

        assertThat(testItem2.getAvailableQuantity()).isEqualTo(12);
        assertThat(testItem2.getReservedQuantity()).isEqualTo(3);

        verify(reservationRepository, times(2)).save(any(StockReservation.class));
        verify(eventProducer, times(1)).sendInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    @DisplayName("Audit #2: Multi-item atomicity - When first item has stock but second fails, NO item stock changes")
    void shouldEnsureMultiItemReservationIsAtomicWhenOneProductHasInsufficientStock() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-atomic", "corr-atomic", "ORDER_CREATED", 2001L, 1L, "cust@test.com",
                new BigDecimal("500.00"), "SUCCESS",
                List.of(
                        new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 2, new BigDecimal("50.00")), // Available: 20 -> OK
                        new OrderCreatedEvent.OrderItemPayload(20L, "TEST-SKU-20", 999, new BigDecimal("50.00")) // Available: 15 -> FAILS!
                ),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(2001L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));
        when(inventoryRepository.findByProductIdWithPessimisticLock(20L)).thenReturn(Optional.of(testItem2));

        inventoryService.processOrderCreated(event);

        // Product 1 stock MUST NOT change (remains 20 available, 0 reserved)
        assertThat(testItem1.getAvailableQuantity()).isEqualTo(20);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(0);

        // Product 2 stock MUST NOT change (remains 15 available, 0 reserved)
        assertThat(testItem2.getAvailableQuantity()).isEqualTo(15);
        assertThat(testItem2.getReservedQuantity()).isEqualTo(0);

        // InventoryItem save must NEVER have been called for either item!
        verify(inventoryRepository, never()).save(any());

        // Must emit InventoryRejectedEvent, NEVER InventoryReservedEvent
        verify(eventProducer, times(1)).sendInventoryRejected(any(InventoryRejectedEvent.class));
        verify(eventProducer, never()).sendInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    @DisplayName("Should emit InventoryRejectedEvent when single item stock is insufficient")
    void shouldRejectWhenStockInsufficient() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-101", "corr-101", "ORDER_CREATED", 1002L, 1L, "cust@test.com",
                new BigDecimal("5000.00"), "SUCCESS",
                List.of(new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 50, new BigDecimal("100.00"))),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(1002L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));

        inventoryService.processOrderCreated(event);

        // Stock must remain unchanged
        assertThat(testItem1.getAvailableQuantity()).isEqualTo(20);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(0);

        ArgumentCaptor<StockReservation> resCaptor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository, times(1)).save(resCaptor.capture());
        assertThat(resCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.REJECTED);

        ArgumentCaptor<InventoryRejectedEvent> captor = ArgumentCaptor.forClass(InventoryRejectedEvent.class);
        verify(eventProducer, times(1)).sendInventoryRejected(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(1002L);
        assertThat(captor.getValue().reason()).contains("Insufficient stock for product ID 10");
        verify(eventProducer, never()).sendInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    @DisplayName("Should emit InventoryRejectedEvent when requested product does not exist in inventory")
    void shouldRejectWhenProductNotFound() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-notfound", "corr-nf", "ORDER_CREATED", 1009L, 1L, "cust@test.com",
                new BigDecimal("99.00"), "SUCCESS",
                List.of(new OrderCreatedEvent.OrderItemPayload(999L, "NON-EXISTENT", 1, new BigDecimal("99.00"))),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(1009L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(999L)).thenReturn(Optional.empty());

        inventoryService.processOrderCreated(event);

        verify(eventProducer, times(1)).sendInventoryRejected(any(InventoryRejectedEvent.class));
        verify(eventProducer, never()).sendInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    @DisplayName("Should prevent duplicate reservations on duplicate OrderCreatedEvent (Idempotency)")
    void shouldPreventDuplicateReservationOnDuplicateOrderCreatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-dup", "corr-dup", "ORDER_CREATED", 1001L, 1L, "cust@test.com",
                new BigDecimal("100.00"), "SUCCESS",
                List.of(new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 2, new BigDecimal("50.00"))),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(1001L)).thenReturn(true);

        inventoryService.processOrderCreated(event);

        verify(inventoryRepository, never()).findByProductIdWithPessimisticLock(any());
        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(eventProducer, never()).sendInventoryReserved(any());
        verify(eventProducer, never()).sendInventoryRejected(any());
    }

    @Test
    @DisplayName("Audit #3: Should handle DataIntegrityViolationException on concurrent duplicate insert")
    void shouldHandleDataIntegrityViolationOnConcurrentDuplicateInsert() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-conc", "corr-conc", "ORDER_CREATED", 3001L, 1L, "cust@test.com",
                new BigDecimal("100.00"), "SUCCESS",
                List.of(new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 1, new BigDecimal("50.00"))),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(3001L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));
        doThrow(new DataIntegrityViolationException("Duplicate entry for uk_stock_reservation_order_product"))
                .when(reservationRepository).save(any(StockReservation.class));

        // Must not crash or propagate DataIntegrityViolationException
        inventoryService.processOrderCreated(event);

        verify(eventProducer, never()).sendInventoryReserved(any());
    }

    @Test
    @DisplayName("Should release reserved stock on PaymentFailed compensation and publish InventoryReleasedEvent")
    void shouldReleaseStockOnPaymentFailedCompensation() {
        StockReservation reservation = StockReservation.builder()
                .id(50L)
                .orderId(1003L)
                .productId(10L)
                .quantity(5)
                .status(ReservationStatus.RESERVED)
                .build();

        testItem1.setAvailableQuantity(15);
        testItem1.setReservedQuantity(5);

        when(reservationRepository.findByOrderId(1003L)).thenReturn(List.of(reservation));
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));

        PaymentFailedEvent failEvent = new PaymentFailedEvent(
                "evt-fail", "corr-fail", "PAYMENT_FAILED", 1003L, "Card declined", Instant.now()
        );

        inventoryService.processPaymentFailedCompensation(failEvent);

        assertThat(testItem1.getAvailableQuantity()).isEqualTo(20);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(0);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        ArgumentCaptor<InventoryReleasedEvent> captor = ArgumentCaptor.forClass(InventoryReleasedEvent.class);
        verify(eventProducer, times(1)).sendInventoryReleased(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(1003L);
        assertThat(captor.getValue().correlationId()).isEqualTo("corr-fail");
        assertThat(captor.getValue().releasedItems().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should be idempotent on duplicate PaymentFailedEvent and never release stock twice")
    void shouldBeIdempotentOnDuplicatePaymentFailedEvent() {
        StockReservation reservation = StockReservation.builder()
                .id(50L)
                .orderId(1003L)
                .productId(10L)
                .quantity(5)
                .status(ReservationStatus.RELEASED) // Already released!
                .build();

        testItem1.setAvailableQuantity(20);
        testItem1.setReservedQuantity(0);

        when(reservationRepository.findByOrderId(1003L)).thenReturn(List.of(reservation));

        PaymentFailedEvent failEvent = new PaymentFailedEvent(
                "evt-fail-2", "corr-fail-2", "PAYMENT_FAILED", 1003L, "Duplicate failure", Instant.now()
        );

        inventoryService.processPaymentFailedCompensation(failEvent);

        assertThat(testItem1.getAvailableQuantity()).isEqualTo(20);
        verify(inventoryRepository, never()).save(any());
        verify(eventProducer, never()).sendInventoryReleased(any());
    }

    @Test
    @DisplayName("Should do nothing on compensation if no reservations exist for orderId")
    void shouldDoNothingOnCompensationIfNoReservationsExist() {
        when(reservationRepository.findByOrderId(9999L)).thenReturn(List.of());

        PaymentFailedEvent failEvent = new PaymentFailedEvent(
                "evt-fail-none", "corr-none", "PAYMENT_FAILED", 9999L, "Failure", Instant.now()
        );

        inventoryService.processPaymentFailedCompensation(failEvent);

        verify(inventoryRepository, never()).save(any());
        verify(eventProducer, never()).sendInventoryReleased(any());
    }

    @Test
    @DisplayName("Should fulfill stock on PaymentSucceeded without returning stock to available")
    void shouldFulfillStockOnPaymentSucceeded() {
        StockReservation reservation = StockReservation.builder()
                .id(60L)
                .orderId(1004L)
                .productId(10L)
                .quantity(3)
                .status(ReservationStatus.RESERVED)
                .build();

        testItem1.setAvailableQuantity(17);
        testItem1.setReservedQuantity(3);

        when(reservationRepository.findByOrderId(1004L)).thenReturn(List.of(reservation));
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));

        PaymentSucceededEvent successEvent = new PaymentSucceededEvent(
                "evt-succ", "corr-succ", "PAYMENT_SUCCEEDED", 1004L, 501L, "TX-12345",
                new BigDecimal("150.00"), Instant.now()
        );

        inventoryService.processPaymentSucceeded(successEvent);

        assertThat(testItem1.getAvailableQuantity()).isEqualTo(17);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(0);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
    }

    @Test
    @DisplayName("Audit #6: Should explicitly throw IllegalStateException if release quantity exceeds reserved stock")
    void shouldThrowExceptionWhenReleasingMoreThanReserved() {
        testItem1.setAvailableQuantity(10);
        testItem1.setReservedQuantity(2);

        // Releasing 5 when only 2 is reserved must throw IllegalStateException (NOT silently clamp)
        assertThatThrownBy(() -> testItem1.release(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot release 5 units for productId 10; only 2 units are currently reserved");

        // Legitimate release of 2 must succeed
        testItem1.release(2);
        assertThat(testItem1.getAvailableQuantity()).isEqualTo(12);
        assertThat(testItem1.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Audit #6: Should explicitly throw IllegalStateException if fulfill quantity exceeds reserved stock")
    void shouldThrowExceptionWhenFulfillingMoreThanReserved() {
        testItem1.setReservedQuantity(2);

        assertThatThrownBy(() -> testItem1.fulfill(3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot fulfill 3 units for productId 10; only 2 units are currently reserved");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when release quantity is zero or negative")
    void shouldThrowExceptionWhenReleaseQuantityIsInvalid() {
        testItem1.setReservedQuantity(5);

        assertThatThrownBy(() -> testItem1.release(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Release quantity must be positive, got: 0");

        assertThatThrownBy(() -> testItem1.release(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Release quantity must be positive, got: -2");
    }

    @Test
    @DisplayName("Should sort product IDs before acquiring pessimistic locks to prevent database deadlocks")
    void shouldSortProductsBeforeLockingToPreventDeadlocks() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-sort", "corr-sort", "ORDER_CREATED", 1010L, 1L, "cust@test.com",
                new BigDecimal("100.00"), "SUCCESS",
                List.of(
                        new OrderCreatedEvent.OrderItemPayload(20L, "TEST-SKU-20", 1, new BigDecimal("50.00")),
                        new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 1, new BigDecimal("50.00"))
                ),
                Instant.now()
        );

        when(reservationRepository.existsByOrderId(1010L)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));
        when(inventoryRepository.findByProductIdWithPessimisticLock(20L)).thenReturn(Optional.of(testItem2));

        inventoryService.processOrderCreated(event);

        InOrder inOrder = inOrder(inventoryRepository);
        inOrder.verify(inventoryRepository).findByProductIdWithPessimisticLock(10L);
        inOrder.verify(inventoryRepository).findByProductIdWithPessimisticLock(20L);
    }

    @Test
    @DisplayName("Should discard invalid OrderCreatedEvent with missing orderId or empty items")
    void shouldDiscardInvalidEventWithMissingOrderIdOrItems() {
        OrderCreatedEvent missingOrderId = new OrderCreatedEvent(
                "evt-inv", "corr-inv", "ORDER_CREATED", null, 1L, "cust@test.com",
                BigDecimal.ZERO, "SUCCESS", List.of(), Instant.now()
        );

        inventoryService.processOrderCreated(missingOrderId);
        verifyNoInteractions(inventoryRepository, reservationRepository, eventProducer);

        OrderCreatedEvent emptyItems = new OrderCreatedEvent(
                "evt-inv2", "corr-inv2", "ORDER_CREATED", 1011L, 1L, "cust@test.com",
                BigDecimal.ZERO, "SUCCESS", List.of(), Instant.now()
        );

        inventoryService.processOrderCreated(emptyItems);
        verifyNoInteractions(inventoryRepository, reservationRepository, eventProducer);
    }

    @Test
    @DisplayName("Should discard OrderCreatedEvent if any item has zero or negative quantity")
    void shouldDiscardEventWithZeroOrNegativeQuantity() {
        OrderCreatedEvent invalidQtyEvent = new OrderCreatedEvent(
                "evt-inv3", "corr-inv3", "ORDER_CREATED", 1012L, 1L, "cust@test.com",
                BigDecimal.ZERO, "SUCCESS",
                List.of(new OrderCreatedEvent.OrderItemPayload(10L, "TEST-SKU-10", 0, new BigDecimal("50.00"))),
                Instant.now()
        );

        inventoryService.processOrderCreated(invalidQtyEvent);
        verifyNoInteractions(inventoryRepository, reservationRepository, eventProducer);
    }

    @Test
    @DisplayName("Should prevent overselling when entity reserve is called with excess quantity")
    void shouldPreventOversellingInDomainModel() {
        testItem1.setAvailableQuantity(5);
        assertThatThrownBy(() -> testItem1.reserve(6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reserve 6 units for productId 10; available stock is 5");
    }

    @Test
    @DisplayName("replenishStock - Should increase available stock and preserve reserved quantity")
    void shouldReplenishStockAndPreserveReserved() {
        testItem1.setAvailableQuantity(20);
        testItem1.setReservedQuantity(5);

        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItem result = inventoryService.replenishStock(10L, 50);

        assertThat(result.getAvailableQuantity()).isEqualTo(70);
        assertThat(result.getReservedQuantity()).isEqualTo(5); // Strictly preserved!
        verify(inventoryRepository, times(1)).findByProductIdWithPessimisticLock(10L);
        verify(inventoryRepository, times(1)).save(testItem1);
    }

    @Test
    @DisplayName("replenishStock - Should accumulate stock correctly across multiple replenishments")
    void shouldAccumulateMultipleReplenishments() {
        testItem1.setAvailableQuantity(10);
        testItem1.setReservedQuantity(0);

        when(inventoryRepository.findByProductIdWithPessimisticLock(10L)).thenReturn(Optional.of(testItem1));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.replenishStock(10L, 30);
        assertThat(testItem1.getAvailableQuantity()).isEqualTo(40);

        inventoryService.replenishStock(10L, 25);
        assertThat(testItem1.getAvailableQuantity()).isEqualTo(65);
        verify(inventoryRepository, times(2)).findByProductIdWithPessimisticLock(10L);
        verify(inventoryRepository, times(2)).save(testItem1);
    }

    @Test
    @DisplayName("replenishStock - Should throw ResourceNotFoundException for unknown product")
    void shouldThrowNotFoundForUnknownProduct() {
        when(inventoryRepository.findByProductIdWithPessimisticLock(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.replenishStock(999L, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product ID 999 not found in inventory");
    }

    @Test
    @DisplayName("replenishStock - Should throw IllegalArgumentException for non-positive quantity")
    void shouldThrowForNonPositiveQuantity() {
        assertThatThrownBy(() -> inventoryService.replenishStock(10L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(() -> inventoryService.replenishStock(10L, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("replenishStock - Should throw IllegalArgumentException for quantity exceeding 10,000")
    void shouldThrowForExcessiveQuantity() {
        assertThatThrownBy(() -> inventoryService.replenishStock(10L, 10001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 10,000");
    }
}
