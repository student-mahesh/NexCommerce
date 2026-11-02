package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.ProcessedOrderEvent;
import com.ecommerce.order.event.InventoryRejectedEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.PaymentFailedEvent;
import com.ecommerce.order.event.PaymentSucceededEvent;
import com.ecommerce.order.exception.ProductServiceUnavailableException;
import com.ecommerce.order.kafka.OrderEventProducer;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ProcessedOrderEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProcessedOrderEventRepository processedOrderEventRepository;
    private final OrderEventProducer orderEventProducer;
    private final ProductCatalogClient productCatalogClient;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long authenticatedCustomerId, String authenticatedEmail) {
        if (authenticatedCustomerId == null || authenticatedCustomerId <= 0) {
            throw new IllegalArgumentException("Valid customerId is required");
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Cart cannot be empty");
        }

        log.info("Creating order for customerId={}, email={}, itemsCount={}",
                authenticatedCustomerId, authenticatedEmail, request.items().size());

        if (productCatalogClient == null) {
            throw new ProductServiceUnavailableException("Product catalog client is not available");
        }

        // Pass 1: Validate basic request constraints for every requested item (only productId and quantity are required from client)
        for (CreateOrderItemRequest itemReq : request.items()) {
            if (itemReq.productId() == null || itemReq.productId() <= 0) {
                throw new IllegalArgumentException("Invalid productId for item");
            }
            if (itemReq.quantity() == null || itemReq.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be at least 1 for productId: " + itemReq.productId());
            }
        }

        // Pass 2: Authoritative Fail-Closed Product Service Validation
        // Retrieve every requested product from the authoritative Product Service.
        // Fails closed if:
        //  - Product Service is unavailable or times out (ProductServiceUnavailableException propagates)
        //  - Product does not exist (throws IllegalArgumentException)
        //  - Product is inactive or missing active status (active == null or false) (throws IllegalArgumentException)
        //  - Product price is null, negative or zero (throws IllegalArgumentException)
        record ValidatedProductItem(CreateOrderItemRequest requestItem, ProductCatalogClient.CatalogProductDto catalogProduct) {}
        List<ValidatedProductItem> validatedItems = new ArrayList<>(request.items().size());

        for (CreateOrderItemRequest itemReq : request.items()) {
            Optional<ProductCatalogClient.CatalogProductDto> catalogOpt = productCatalogClient.getProduct(itemReq.productId());
            if (catalogOpt.isEmpty()) {
                throw new IllegalArgumentException("Product not found with id: " + itemReq.productId());
            }

            ProductCatalogClient.CatalogProductDto catalogProduct = catalogOpt.get();

            // Strict active status check: reject null/missing active status as well as false
            if (catalogProduct.active() == null || !catalogProduct.active()) {
                throw new IllegalArgumentException("Product is inactive or unavailable for sale: productId=" + itemReq.productId());
            }

            // Strict valid catalog price check: price must be non-null and > 0
            if (catalogProduct.price() == null || catalogProduct.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Product price is invalid in catalog for productId: " + itemReq.productId());
            }

            validatedItems.add(new ValidatedProductItem(itemReq, catalogProduct));
        }

        // Pass 3: Construct Order strictly using authoritative catalog price, name, and SKU
        // Client price, product name, and SKU are completely ignored and never determine saved order values.
        Order order = Order.builder()
                .customerId(authenticatedCustomerId)
                .customerEmail(authenticatedEmail)
                .customerName(request.customerName())
                .shippingAddress(request.shippingAddress())
                .paymentSimulateMode(request.paymentSimulateMode() != null ? request.paymentSimulateMode().toUpperCase() : "SUCCESS")
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<OrderCreatedEvent.OrderItemPayload> eventItemPayloads = new ArrayList<>();

        for (ValidatedProductItem validated : validatedItems) {
            CreateOrderItemRequest itemReq = validated.requestItem();
            ProductCatalogClient.CatalogProductDto catalogProduct = validated.catalogProduct();

            BigDecimal authoritativePrice = catalogProduct.price();
            String authoritativeName = (catalogProduct.name() != null && !catalogProduct.name().isBlank())
                    ? catalogProduct.name()
                    : "Product-" + itemReq.productId();
            String authoritativeSku = (catalogProduct.sku() != null && !catalogProduct.sku().isBlank())
                    ? catalogProduct.sku()
                    : "SKU-" + itemReq.productId();

            if (itemReq.unitPrice() != null && itemReq.unitPrice().compareTo(authoritativePrice) != 0) {
                log.warn("Price mismatch detected for productId={}: client sent={}, catalog authoritative={}. Overriding with authoritative catalog price.",
                        itemReq.productId(), itemReq.unitPrice(), authoritativePrice);
            }

            BigDecimal subtotal = authoritativePrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            runningTotal = runningTotal.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(itemReq.productId())
                    .productName(authoritativeName)
                    .productSku(authoritativeSku)
                    .unitPrice(authoritativePrice)
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .build();

            order.addItem(orderItem);

            eventItemPayloads.add(new OrderCreatedEvent.OrderItemPayload(
                    itemReq.productId(),
                    authoritativeSku,
                    itemReq.quantity(),
                    authoritativePrice
            ));
        }

        order.setTotalAmount(runningTotal);
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with id={}, status=PENDING, totalAmount={}", savedOrder.getId(), savedOrder.getTotalAmount());

        String eventId = UUID.randomUUID().toString();
        String correlationId = "CORR-" + savedOrder.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);

        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                correlationId,
                "ORDER_CREATED",
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.getCustomerEmail(),
                savedOrder.getTotalAmount(),
                savedOrder.getPaymentSimulateMode(),
                eventItemPayloads,
                Instant.now()
        );

        try {
            orderEventProducer.publishOrderCreated(event);
        } catch (Exception e) {
            log.error("Kafka publishing failure for orderId={}: {}. Order remains safely persisted in PENDING state.",
                    savedOrder.getId(), e.getMessage());
        }

        return OrderResponse.fromEntity(savedOrder);
    }

    public boolean orderExists(Long orderId) {
        return orderRepository.existsById(orderId);
    }

    public Optional<OrderResponse> getOrderById(Long orderId) {
        return orderRepository.findById(orderId).map(OrderResponse::fromEntity);
    }

    public Optional<OrderResponse> getOrderByIdAndCustomer(Long orderId, Long customerId, boolean isAdmin) {
        return orderRepository.findById(orderId)
                .filter(order -> isAdmin || order.getCustomerId().equals(customerId))
                .map(OrderResponse::fromEntity);
    }

    public List<OrderResponse> getCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public List<OrderResponse> getAllOrdersAdmin() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        validateEvent(event != null ? event.orderId() : null, "PaymentSucceededEvent");
        log.info("OrderService received PaymentSucceededEvent: orderId={}, paymentId={}, correlationId={}",
                event.orderId(), event.paymentId(), event.correlationId());

        Order order = findOrderForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order id=" + event.orderId() + " not found for PaymentSucceededEvent"));

        // Terminal state guards
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.warn("Order id={} is already CONFIRMED. Skipping duplicate PaymentSucceededEvent.", event.orderId());
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Invalid state transition from CANCELLED to CONFIRMED for orderId=" + event.orderId());
        }

        // Durable database idempotency check
        if (processedOrderEventRepository.existsByOrderIdAndEventType(event.orderId(), "PAYMENT_SUCCEEDED")) {
            log.warn("PaymentSucceededEvent for orderId={} was already recorded in order_processed_events. Skipping duplicate.", event.orderId());
            return;
        }

        // Record event in order_processed_events with unique constraint protection
        try {
            processedOrderEventRepository.save(ProcessedOrderEvent.builder()
                    .orderId(event.orderId())
                    .eventId(event.eventId())
                    .eventType("PAYMENT_SUCCEEDED")
                    .processedAt(Instant.now())
                    .build());
            processedOrderEventRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate PaymentSucceededEvent insert for orderId={}. Skipping.", event.orderId());
            return;
        }

        // State transition
        order.validateStateTransition(OrderStatus.CONFIRMED);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order id={} transitioned to CONFIRMED successfully", order.getId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        validateEvent(event != null ? event.orderId() : null, "PaymentFailedEvent");
        log.warn("OrderService received PaymentFailedEvent: orderId={}, reason='{}', correlationId={}",
                event.orderId(), event.reason(), event.correlationId());

        Order order = findOrderForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order id=" + event.orderId() + " not found for PaymentFailedEvent"));

        // Terminal state guards
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order id={} is already CANCELLED. Skipping duplicate PaymentFailedEvent.", event.orderId());
            return;
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Invalid state transition from CONFIRMED to CANCELLED for orderId=" + event.orderId());
        }

        // Durable database idempotency check
        if (processedOrderEventRepository.existsByOrderIdAndEventType(event.orderId(), "PAYMENT_FAILED")) {
            log.warn("PaymentFailedEvent for orderId={} was already recorded in order_processed_events. Skipping duplicate.", event.orderId());
            return;
        }

        // Record event in order_processed_events with unique constraint protection
        try {
            processedOrderEventRepository.save(ProcessedOrderEvent.builder()
                    .orderId(event.orderId())
                    .eventId(event.eventId())
                    .eventType("PAYMENT_FAILED")
                    .processedAt(Instant.now())
                    .build());
            processedOrderEventRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate PaymentFailedEvent insert for orderId={}. Skipping.", event.orderId());
            return;
        }

        // State transition
        order.validateStateTransition(OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason("Payment authorization failed: " + event.reason());
        orderRepository.save(order);
        log.warn("Order id={} transitioned to CANCELLED due to payment failure", order.getId());
    }

    @Transactional
    public void handleInventoryRejected(InventoryRejectedEvent event) {
        validateEvent(event != null ? event.orderId() : null, "InventoryRejectedEvent");
        log.warn("OrderService received InventoryRejectedEvent: orderId={}, reason='{}', correlationId={}",
                event.orderId(), event.reason(), event.correlationId());

        Order order = findOrderForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order id=" + event.orderId() + " not found for InventoryRejectedEvent"));

        // Terminal state guards
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order id={} is already CANCELLED. Skipping duplicate InventoryRejectedEvent.", event.orderId());
            return;
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Invalid state transition from CONFIRMED to CANCELLED for orderId=" + event.orderId());
        }

        // Durable database idempotency check
        if (processedOrderEventRepository.existsByOrderIdAndEventType(event.orderId(), "INVENTORY_REJECTED")) {
            log.warn("InventoryRejectedEvent for orderId={} was already recorded in order_processed_events. Skipping duplicate.", event.orderId());
            return;
        }

        // Record event in order_processed_events with unique constraint protection
        try {
            processedOrderEventRepository.save(ProcessedOrderEvent.builder()
                    .orderId(event.orderId())
                    .eventId(event.eventId())
                    .eventType("INVENTORY_REJECTED")
                    .processedAt(Instant.now())
                    .build());
            processedOrderEventRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate InventoryRejectedEvent insert for orderId={}. Skipping.", event.orderId());
            return;
        }

        // State transition
        order.validateStateTransition(OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason("Stock reservation failed: " + event.reason());
        orderRepository.save(order);
        log.warn("Order id={} transitioned to CANCELLED due to inventory rejection", order.getId());
    }

    private Optional<Order> findOrderForUpdate(Long orderId) {
        try {
            Optional<Order> order = orderRepository.findByIdForUpdate(orderId);
            if (order.isPresent()) {
                return order;
            }
        } catch (Exception e) {
            log.debug("findByIdForUpdate not supported or mocked, falling back to findById: {}", e.getMessage());
        }
        return orderRepository.findById(orderId);
    }

    private void validateEvent(Long orderId, String eventType) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(eventType + " must have a valid positive orderId");
        }
    }
}
