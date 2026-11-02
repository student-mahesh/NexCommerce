package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.InventoryRejectedEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.PaymentFailedEvent;
import com.ecommerce.order.event.PaymentSucceededEvent;
import com.ecommerce.order.kafka.OrderEventProducer;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ProcessedOrderEventRepository;
import com.ecommerce.order.entity.ProcessedOrderEvent;
import com.ecommerce.order.exception.ProductServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedOrderEventRepository processedOrderEventRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private ProductCatalogClient productCatalogClient;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest createOrderRequest;

    @BeforeEach
    void setUp() {
        createOrderRequest = new CreateOrderRequest(
                "John Doe",
                "123 Tech Street, Silicon Valley, CA",
                "SUCCESS",
                List.of(
                        new CreateOrderItemRequest(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("129.99"), 2),
                        new CreateOrderItemRequest(2L, "SonicWave Pro Headphones", "TECH-HP-02", new BigDecimal("89.99"), 1)
                )
        );

        lenient().when(productCatalogClient.getProduct(1L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("129.99"), true)
        ));
        lenient().when(productCatalogClient.getProduct(2L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(2L, "SonicWave Pro Headphones", "TECH-HP-02", new BigDecimal("89.99"), true)
        ));
    }

    @Test
    @DisplayName("Should create order with PENDING status, compute total, and publish OrderCreatedEvent")
    void testCreateOrder() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(101L);
            return order;
        });

        OrderResponse response = orderService.createOrder(createOrderRequest, 1L, "customer@test.com");

        assertNotNull(response);
        assertEquals(101L, response.id());
        assertEquals("ORD-101", response.orderNumber());
        assertEquals(OrderStatus.PENDING, response.status());
        // 129.99 * 2 = 259.98; 89.99 * 1 = 89.99; sum = 349.97
        assertEquals(new BigDecimal("349.97"), response.totalAmount());
        assertEquals(2, response.items().size());

        verify(orderRepository, times(1)).save(any(Order.class));

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(orderEventProducer, times(1)).publishOrderCreated(captor.capture());
        OrderCreatedEvent published = captor.getValue();
        assertEquals(101L, published.orderId());
        assertNotNull(published.correlationId());
        assertEquals(new BigDecimal("349.97"), published.totalAmount());
        assertEquals(2, published.items().size());
    }

    @Test
    @DisplayName("Should reject order creation when cart is empty")
    void testCreateOrderEmptyCart() {
        CreateOrderRequest emptyRequest = new CreateOrderRequest(
                "John Doe", "123 Tech Street", "SUCCESS", Collections.emptyList()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(emptyRequest, 1L, "customer@test.com")
        );
        assertTrue(ex.getMessage().contains("Cart cannot be empty"));
        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }

    @Test
    @DisplayName("Should reject order creation when customerId is missing or non-positive")
    void testCreateOrderInvalidCustomerId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(createOrderRequest, null, "customer@test.com")
        );
        assertTrue(ex.getMessage().contains("Valid customerId is required"));

        assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(createOrderRequest, 0L, "customer@test.com")
        );
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject order creation when item productId is invalid")
    void testCreateOrderInvalidProductId() {
        CreateOrderRequest badRequest = new CreateOrderRequest(
                "John Doe", "123 Tech Street", "SUCCESS",
                List.of(new CreateOrderItemRequest(null, "Test", "SKU", new BigDecimal("10.00"), 1))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(badRequest, 1L, "customer@test.com")
        );
        assertTrue(ex.getMessage().contains("Invalid productId"));
    }

    @Test
    @DisplayName("Should reject order creation when item quantity is zero or negative")
    void testCreateOrderInvalidQuantity() {
        CreateOrderRequest badRequest = new CreateOrderRequest(
                "John Doe", "123 Tech Street", "SUCCESS",
                List.of(new CreateOrderItemRequest(1L, "Test", "SKU", new BigDecimal("10.00"), 0))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(badRequest, 1L, "customer@test.com")
        );
        assertTrue(ex.getMessage().contains("Quantity must be at least 1"));
    }

    @Test
    @DisplayName("Should create order successfully when client unit price, name, and SKU are null (client price independence)")
    void testCreateOrder_NullClientPriceAndMetadata_SucceedsUsingCatalog() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(101L);
            return order;
        });

        // Request has null unitPrice, null productName, null productSku
        CreateOrderRequest nullPriceRequest = new CreateOrderRequest(
                "John Doe",
                "123 Tech Street, Silicon Valley, CA",
                "SUCCESS",
                List.of(
                        new CreateOrderItemRequest(1L, null, null, null, 2),
                        new CreateOrderItemRequest(2L, null, null, null, 1)
                )
        );

        OrderResponse response = orderService.createOrder(nullPriceRequest, 1L, "customer@test.com");

        assertNotNull(response);
        assertEquals(101L, response.id());
        assertEquals(new BigDecimal("349.97"), response.totalAmount());
        assertEquals(2, response.items().size());
        assertEquals(new BigDecimal("129.99"), response.items().get(0).unitPrice());
        assertEquals("Apex Pro Keyboard", response.items().get(0).productName());
        assertEquals("TECH-KB-01", response.items().get(0).productSku());
        assertEquals(new BigDecimal("89.99"), response.items().get(1).unitPrice());

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(orderEventProducer, times(1)).publishOrderCreated(captor.capture());
        OrderCreatedEvent published = captor.getValue();
        assertEquals(new BigDecimal("349.97"), published.totalAmount());
        assertEquals(new BigDecimal("129.99"), published.items().get(0).unitPrice());
        assertEquals("TECH-KB-01", published.items().get(0).sku());
    }

    @Test
    @DisplayName("Should ignore negative or zero client-supplied price in favor of authoritative catalog price")
    void testCreateOrder_NegativeClientPrice_IgnoredInFavorOfCatalogPrice() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(102L);
            return order;
        });

        CreateOrderRequest negativePriceRequest = new CreateOrderRequest(
                "John Doe",
                "123 Tech Street, Silicon Valley, CA",
                "SUCCESS",
                List.of(
                        new CreateOrderItemRequest(1L, "Test", "SKU", new BigDecimal("-5.00"), 1)
                )
        );

        OrderResponse response = orderService.createOrder(negativePriceRequest, 1L, "customer@test.com");

        assertNotNull(response);
        assertEquals(new BigDecimal("129.99"), response.totalAmount());
        assertEquals(new BigDecimal("129.99"), response.items().get(0).unitPrice());
    }

    @Test
    @DisplayName("Should preserve saved order in PENDING state even if Kafka publishing throws an exception")
    void testCreateOrderKafkaPublishFailure() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(200L);
            return order;
        });

        doThrow(new RuntimeException("Kafka broker unreachable"))
                .when(orderEventProducer).publishOrderCreated(any(OrderCreatedEvent.class));

        OrderResponse response = orderService.createOrder(createOrderRequest, 1L, "customer@test.com");

        assertNotNull(response);
        assertEquals(200L, response.id());
        assertEquals(OrderStatus.PENDING, response.status());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should return order if customer owns it or user is admin")
    void testGetOrderByIdAndCustomer() {
        Order order = Order.builder()
                .id(300L)
                .customerId(10L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .build();

        when(orderRepository.findById(300L)).thenReturn(Optional.of(order));

        // Customer owns order -> success
        Optional<OrderResponse> ownerResult = orderService.getOrderByIdAndCustomer(300L, 10L, false);
        assertTrue(ownerResult.isPresent());
        assertEquals(300L, ownerResult.get().id());

        // Admin views order -> success
        Optional<OrderResponse> adminResult = orderService.getOrderByIdAndCustomer(300L, 999L, true);
        assertTrue(adminResult.isPresent());

        // Different customer views order -> empty (denied)
        Optional<OrderResponse> strangerResult = orderService.getOrderByIdAndCustomer(300L, 20L, false);
        assertTrue(strangerResult.isEmpty());
    }

    @Test
    @DisplayName("Should confirm order upon receiving PaymentSucceededEvent")
    void testHandlePaymentSucceeded() {
        Order pendingOrder = Order.builder()
                .id(101L)
                .customerId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("349.97"))
                .build();

        when(orderRepository.findById(101L)).thenReturn(Optional.of(pendingOrder));

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                "PaymentSucceededEvent",
                101L,
                1L,
                "TXN-AUTH-1234",
                new BigDecimal("349.97"),
                Instant.now()
        );

        orderService.handlePaymentSucceeded(event);

        assertEquals(OrderStatus.CONFIRMED, pendingOrder.getStatus());
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    @Test
    @DisplayName("Should idempotently ignore PaymentSucceededEvent if order is already CONFIRMED")
    void testHandlePaymentSucceededIdempotent() {
        Order confirmedOrder = Order.builder()
                .id(101L)
                .customerId(1L)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("349.97"))
                .build();

        when(orderRepository.findById(101L)).thenReturn(Optional.of(confirmedOrder));

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID().toString(), "PaymentSucceededEvent", 101L, 1L, "TXN-1", new BigDecimal("349.97"), Instant.now()
        );

        orderService.handlePaymentSucceeded(event);

        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel order upon receiving PaymentFailedEvent")
    void testHandlePaymentFailed() {
        Order pendingOrder = Order.builder()
                .id(102L)
                .customerId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        when(orderRepository.findById(102L)).thenReturn(Optional.of(pendingOrder));

        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID().toString(),
                "PaymentFailedEvent",
                102L,
                "Simulated card decline",
                Instant.now()
        );

        orderService.handlePaymentFailed(event);

        assertEquals(OrderStatus.CANCELLED, pendingOrder.getStatus());
        assertTrue(pendingOrder.getCancellationReason().contains("Simulated card decline"));
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    @Test
    @DisplayName("Should idempotently ignore PaymentFailedEvent if order is already CANCELLED")
    void testHandlePaymentFailedIdempotent() {
        Order cancelledOrder = Order.builder()
                .id(102L)
                .customerId(1L)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        when(orderRepository.findById(102L)).thenReturn(Optional.of(cancelledOrder));

        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID().toString(), "PaymentFailedEvent", 102L, "Card error", Instant.now()
        );

        orderService.handlePaymentFailed(event);

        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel order upon receiving InventoryRejectedEvent")
    void testHandleInventoryRejected() {
        Order pendingOrder = Order.builder()
                .id(103L)
                .customerId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        when(orderRepository.findById(103L)).thenReturn(Optional.of(pendingOrder));

        InventoryRejectedEvent event = new InventoryRejectedEvent(
                UUID.randomUUID().toString(),
                "InventoryRejectedEvent",
                103L,
                "Insufficient stock for productId=1",
                Instant.now()
        );

        orderService.handleInventoryRejected(event);

        assertEquals(OrderStatus.CANCELLED, pendingOrder.getStatus());
        assertTrue(pendingOrder.getCancellationReason().contains("Insufficient stock"));
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    @Test
    @DisplayName("Should throw IllegalStateException on invalid state transition from CONFIRMED to CANCELLED")
    void testInvalidTransitionConfirmedToCancelled() {
        Order confirmedOrder = Order.builder()
                .id(104L)
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderRepository.findById(104L)).thenReturn(Optional.of(confirmedOrder));

        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID().toString(), "PaymentFailedEvent", 104L, "Late failure", Instant.now()
        );

        assertThrows(IllegalStateException.class, () -> orderService.handlePaymentFailed(event));
    }

    @Test
    @DisplayName("Should throw IllegalStateException on invalid state transition from CANCELLED to CONFIRMED")
    void testInvalidTransitionCancelledToConfirmed() {
        Order cancelledOrder = Order.builder()
                .id(105L)
                .status(OrderStatus.CANCELLED)
                .build();

        when(orderRepository.findById(105L)).thenReturn(Optional.of(cancelledOrder));

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID().toString(), "PaymentSucceededEvent", 105L, 1L, "TXN-LATE", new BigDecimal("50.00"), Instant.now()
        );

        assertThrows(IllegalStateException.class, () -> orderService.handlePaymentSucceeded(event));
    }

    @Test
    @DisplayName("Should retrieve order history for customer")
    void testGetCustomerOrders() {
        Order o1 = Order.builder().id(1L).customerId(5L).status(OrderStatus.CONFIRMED).totalAmount(new BigDecimal("20.00")).build();
        Order o2 = Order.builder().id(2L).customerId(5L).status(OrderStatus.PENDING).totalAmount(new BigDecimal("30.00")).build();

        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(o2, o1));

        List<OrderResponse> results = orderService.getCustomerOrders(5L);
        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).id());
        assertEquals(1L, results.get(1).id());
    }

    @Test
    @DisplayName("Should ignore PaymentSucceededEvent if already recorded in order_processed_events")
    void testHandlePaymentSucceeded_DuplicateRecordedInProcessedEvents() {
        Order pendingOrder = Order.builder().id(101L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(101L)).thenReturn(Optional.of(pendingOrder));
        when(processedOrderEventRepository.existsByOrderIdAndEventType(101L, "PAYMENT_SUCCEEDED")).thenReturn(true);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID().toString(), "PaymentSucceededEvent", 101L, 1L, "TXN-1", new BigDecimal("100.00"), Instant.now()
        );

        orderService.handlePaymentSucceeded(event);

        assertEquals(OrderStatus.PENDING, pendingOrder.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle concurrent duplicate PaymentSucceededEvent insert via DataIntegrityViolationException")
    void testHandlePaymentSucceeded_ConcurrentRaceCondition() {
        Order pendingOrder = Order.builder().id(101L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(101L)).thenReturn(Optional.of(pendingOrder));
        when(processedOrderEventRepository.existsByOrderIdAndEventType(101L, "PAYMENT_SUCCEEDED")).thenReturn(false);
        when(processedOrderEventRepository.save(any(ProcessedOrderEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_order_event_type"));

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID().toString(), "PaymentSucceededEvent", 101L, 1L, "TXN-1", new BigDecimal("100.00"), Instant.now()
        );

        orderService.handlePaymentSucceeded(event);

        assertEquals(OrderStatus.PENDING, pendingOrder.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when handling event with null or invalid orderId")
    void testHandleEvents_InvalidOrderId() {
        PaymentSucceededEvent invalidSucceeded = new PaymentSucceededEvent(
                "evt-1", "PaymentSucceededEvent", null, 1L, "TXN-1", BigDecimal.TEN, Instant.now()
        );
        assertThrows(IllegalArgumentException.class, () -> orderService.handlePaymentSucceeded(invalidSucceeded));

        PaymentFailedEvent invalidFailed = new PaymentFailedEvent(
                "evt-2", "PaymentFailedEvent", -1L, "Declined", Instant.now()
        );
        assertThrows(IllegalArgumentException.class, () -> orderService.handlePaymentFailed(invalidFailed));

        InventoryRejectedEvent invalidRejected = new InventoryRejectedEvent(
                "evt-3", "InventoryRejectedEvent", 0L, "Out of stock", Instant.now()
        );
        assertThrows(IllegalArgumentException.class, () -> orderService.handleInventoryRejected(invalidRejected));
    }

    @Test
    @DisplayName("Should detect and override client price tampering using authoritative catalog price")
    void testCreateOrderOverridesTamperedPriceWithAuthoritativeCatalogPrice() {
        // Customer attempts to tamper with the price, sending $1.00 instead of $129.99
        CreateOrderRequest tamperedRequest = new CreateOrderRequest(
                "Alice Hacker",
                "404 Dark Web Ave",
                "SUCCESS",
                List.of(
                        new CreateOrderItemRequest(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("1.00"), 2)
                )
        );

        // Product Catalog client returns the true authoritative price ($129.99)
        when(productCatalogClient.getProduct(1L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("129.99"), true)
        ));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(202L);
            return order;
        });

        OrderResponse response = orderService.createOrder(tamperedRequest, 1L, "alice@test.com");

        assertNotNull(response);
        assertEquals(202L, response.id());
        // Price should be overridden from $1.00 * 2 = $2.00 to authoritative $129.99 * 2 = $259.98
        assertEquals(new BigDecimal("259.98"), response.totalAmount());
        assertEquals(new BigDecimal("129.99"), response.items().get(0).unitPrice());
        assertEquals(new BigDecimal("259.98"), response.items().get(0).subtotal());

        // Verify Kafka event published has the authoritative total
        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(orderEventProducer, times(1)).publishOrderCreated(captor.capture());
        assertEquals(new BigDecimal("259.98"), captor.getValue().totalAmount());
        assertEquals(new BigDecimal("129.99"), captor.getValue().items().get(0).unitPrice());
    }

    @Test
    @DisplayName("Should reject order creation with ProductServiceUnavailableException when Product Service is unavailable")
    void testCreateOrder_ProductServiceUnavailable() {
        when(productCatalogClient.getProduct(1L))
                .thenThrow(new ProductServiceUnavailableException("Product Service is currently unavailable. Please try again later."));

        CreateOrderRequest request = new CreateOrderRequest(
                "Bob", "123 Main St", "SUCCESS",
                List.of(new CreateOrderItemRequest(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("129.99"), 1))
        );

        ProductServiceUnavailableException ex = assertThrows(ProductServiceUnavailableException.class, () ->
                orderService.createOrder(request, 1L, "bob@test.com")
        );
        assertTrue(ex.getMessage().contains("Product Service is currently unavailable"));

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }

    @Test
    @DisplayName("Should reject order creation when product active status is null (missing/unknown)")
    void testCreateOrder_NullActiveStatus_Rejected() {
        when(productCatalogClient.getProduct(1L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(1L, "Unknown Status Keyboard", "TECH-KB-01", new BigDecimal("129.99"), null)
        ));

        CreateOrderRequest request = new CreateOrderRequest(
                "Bob", "123 Main St", "SUCCESS",
                List.of(new CreateOrderItemRequest(1L, "Unknown Status Keyboard", "TECH-KB-01", new BigDecimal("129.99"), 1))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(request, 1L, "bob@test.com")
        );
        assertTrue(ex.getMessage().contains("Product is inactive or unavailable for sale"));

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }

    @Test
    @DisplayName("Should reject order creation when product is not found in catalog, saving no order and publishing no event")
    void testCreateOrder_ProductNotFound() {
        when(productCatalogClient.getProduct(999L)).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
                "Bob", "123 Main St", "SUCCESS",
                List.of(new CreateOrderItemRequest(999L, "Ghost Product", "SKU-999", new BigDecimal("50.00"), 1))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(request, 1L, "bob@test.com")
        );
        assertTrue(ex.getMessage().contains("Product not found"));

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }

    @Test
    @DisplayName("Should reject order creation when catalog price is invalid (null or <= 0)")
    void testCreateOrder_InvalidCatalogPrice() {
        when(productCatalogClient.getProduct(1L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(1L, "Free Glitch Item", "SKU-GLITCH", BigDecimal.ZERO, true)
        ));

        CreateOrderRequest request = new CreateOrderRequest(
                "Bob", "123 Main St", "SUCCESS",
                List.of(new CreateOrderItemRequest(1L, "Free Glitch Item", "SKU-GLITCH", new BigDecimal("10.00"), 1))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(request, 1L, "bob@test.com")
        );
        assertTrue(ex.getMessage().contains("Product price is invalid in catalog"));

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }

    @Test
    @DisplayName("Should reject order creation when product is inactive or unavailable for sale")
    void testCreateOrder_InactiveProduct() {
        when(productCatalogClient.getProduct(1L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(1L, "Discontinued Keyboard", "TECH-KB-01", new BigDecimal("129.99"), false)
        ));

        CreateOrderRequest request = new CreateOrderRequest(
                "Bob", "123 Main St", "SUCCESS",
                List.of(new CreateOrderItemRequest(1L, "Discontinued Keyboard", "TECH-KB-01", new BigDecimal("129.99"), 1))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(request, 1L, "bob@test.com")
        );
        assertTrue(ex.getMessage().contains("Product is inactive or unavailable for sale"));

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }

    @Test
    @DisplayName("Should validate all products in multi-item order before any persistence or Kafka publishing")
    void testCreateOrder_MultipleProducts_AllMustValidate() {
        // Item 1 (productId=1) is valid
        when(productCatalogClient.getProduct(1L)).thenReturn(Optional.of(
                new ProductCatalogClient.CatalogProductDto(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("129.99"), true)
        ));
        // Item 2 (productId=2) is not found
        when(productCatalogClient.getProduct(2L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(createOrderRequest, 1L, "customer@test.com")
        );
        assertTrue(ex.getMessage().contains("Product not found with id: 2"));

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderCreated(any());
    }
}
