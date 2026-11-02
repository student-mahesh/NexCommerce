package com.ecommerce.order.kafka;

import com.ecommerce.order.event.InventoryRejectedEvent;
import com.ecommerce.order.event.PaymentFailedEvent;
import com.ecommerce.order.event.PaymentSucceededEvent;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaResponseListenerTest {

    @Mock
    private OrderService orderService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OrderSagaResponseListener listener;

    private String validPaymentSucceededPayload;
    private String validPaymentFailedPayload;
    private String validInventoryRejectedPayload;

    @BeforeEach
    void setUp() throws Exception {
        PaymentSucceededEvent succEvent = new PaymentSucceededEvent(
                "evt-1", "corr-1", "PaymentSucceededEvent", 101L, 1L, "TXN-1", new BigDecimal("149.99"), Instant.now()
        );
        validPaymentSucceededPayload = objectMapper.writeValueAsString(succEvent);

        PaymentFailedEvent failEvent = new PaymentFailedEvent(
                "evt-2", "corr-2", "PaymentFailedEvent", 102L, "Card declined", Instant.now()
        );
        validPaymentFailedPayload = objectMapper.writeValueAsString(failEvent);

        InventoryRejectedEvent rejEvent = new InventoryRejectedEvent(
                "evt-3", "corr-3", "InventoryRejectedEvent", 103L, "Out of stock", Instant.now()
        );
        validInventoryRejectedPayload = objectMapper.writeValueAsString(rejEvent);
    }

    @Test
    @DisplayName("Should parse and delegate valid PaymentSucceededEvent")
    void testOnPaymentSucceeded_Valid() {
        listener.onPaymentSucceeded(validPaymentSucceededPayload);
        verify(orderService, times(1)).handlePaymentSucceeded(any(PaymentSucceededEvent.class));
    }

    @Test
    @DisplayName("Should discard poison pill on payment-succeeded-events without throwing")
    void testOnPaymentSucceeded_PoisonPill() {
        listener.onPaymentSucceeded("malformed_json_{");
        verify(orderService, never()).handlePaymentSucceeded(any());
    }

    @Test
    @DisplayName("Should safely discard late/conflicting PaymentSucceededEvent throwing IllegalStateException")
    void testOnPaymentSucceeded_ConflictingEventDiscarded() {
        doThrow(new IllegalStateException("Invalid state transition from CANCELLED to CONFIRMED"))
                .when(orderService).handlePaymentSucceeded(any());

        // Must not throw
        listener.onPaymentSucceeded(validPaymentSucceededPayload);
        verify(orderService, times(1)).handlePaymentSucceeded(any());
    }

    @Test
    @DisplayName("Should rethrow database/unexpected runtime exception on PaymentSucceededEvent to trigger Kafka retry")
    void testOnPaymentSucceeded_RethrowOnDatabaseFailure() {
        doThrow(new RuntimeException("Database connection timeout"))
                .when(orderService).handlePaymentSucceeded(any());

        assertThatThrownBy(() -> listener.onPaymentSucceeded(validPaymentSucceededPayload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timeout");
    }

    @Test
    @DisplayName("Should parse and delegate valid PaymentFailedEvent")
    void testOnPaymentFailed_Valid() {
        listener.onPaymentFailed(validPaymentFailedPayload);
        verify(orderService, times(1)).handlePaymentFailed(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("Should discard poison pill on payment-failed-events without throwing")
    void testOnPaymentFailed_PoisonPill() {
        listener.onPaymentFailed("malformed_json_{");
        verify(orderService, never()).handlePaymentFailed(any());
    }

    @Test
    @DisplayName("Should parse and delegate valid InventoryRejectedEvent")
    void testOnInventoryRejected_Valid() {
        listener.onInventoryRejected(validInventoryRejectedPayload);
        verify(orderService, times(1)).handleInventoryRejected(any(InventoryRejectedEvent.class));
    }

    @Test
    @DisplayName("Should discard poison pill on inventory-rejected-events without throwing")
    void testOnInventoryRejected_PoisonPill() {
        listener.onInventoryRejected("malformed_json_{");
        verify(orderService, never()).handleInventoryRejected(any());
    }
}

