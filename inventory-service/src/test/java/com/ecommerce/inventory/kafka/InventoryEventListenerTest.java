package com.ecommerce.inventory.kafka;

import com.ecommerce.inventory.event.OrderCreatedEvent;
import com.ecommerce.inventory.event.PaymentFailedEvent;
import com.ecommerce.inventory.event.PaymentSucceededEvent;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock
    private InventoryService inventoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private InventoryEventListener eventListener;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should successfully deserialize and process OrderCreatedEvent")
    void shouldProcessOrderCreated() {
        String json = """
                {
                    "eventId": "evt-1",
                    "correlationId": "corr-1",
                    "eventType": "ORDER_CREATED",
                    "orderId": 100,
                    "customerId": 1,
                    "customerEmail": "test@test.com",
                    "totalAmount": 199.99,
                    "paymentSimulateMode": "SUCCESS",
                    "items": [
                        {
                            "productId": 1,
                            "sku": "SKU-1",
                            "quantity": 2,
                            "unitPrice": 99.99
                        }
                    ],
                    "timestamp": "2026-10-30T10:00:00Z"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-created-events", 0, 0, "100", json);
        eventListener.onOrderCreated(record);

        verify(inventoryService, times(1)).processOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("Should discard malformed JSON poison pill without rethrowing exception")
    void shouldDiscardMalformedJsonWithoutThrowingException() {
        String badJson = "{ invalid json payload ";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-created-events", 0, 0, "101", badJson);

        eventListener.onOrderCreated(record);

        verify(inventoryService, never()).processOrderCreated(any());
    }

    @Test
    @DisplayName("Should rethrow exception when InventoryService fails to prevent offset commit")
    void shouldRethrowWhenInventoryServiceFails() {
        String json = """
                {
                    "eventId": "evt-err",
                    "correlationId": "corr-err",
                    "eventType": "ORDER_CREATED",
                    "orderId": 105,
                    "customerId": 1,
                    "customerEmail": "test@test.com",
                    "totalAmount": 199.99,
                    "paymentSimulateMode": "SUCCESS",
                    "items": [
                        {
                            "productId": 1,
                            "sku": "SKU-1",
                            "quantity": 2,
                            "unitPrice": 99.99
                        }
                    ],
                    "timestamp": "2026-10-30T10:00:00Z"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-created-events", 0, 0, "105", json);
        doThrow(new RuntimeException("Database connection timeout")).when(inventoryService).processOrderCreated(any());

        assertThatThrownBy(() -> eventListener.onOrderCreated(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timeout");
    }

    @Test
    @DisplayName("Should deserialize and process PaymentFailedEvent")
    void shouldProcessPaymentFailed() {
        String json = """
                {
                    "eventId": "evt-fail",
                    "correlationId": "corr-fail",
                    "eventType": "PAYMENT_FAILED",
                    "orderId": 102,
                    "reason": "Declined",
                    "timestamp": "2026-10-30T10:00:00Z"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-failed-events", 0, 0, "102", json);
        eventListener.onPaymentFailed(record);

        verify(inventoryService, times(1)).processPaymentFailedCompensation(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("Should deserialize and process PaymentSucceededEvent")
    void shouldProcessPaymentSucceeded() {
        String json = """
                {
                    "eventId": "evt-succ",
                    "correlationId": "corr-succ",
                    "eventType": "PAYMENT_SUCCEEDED",
                    "orderId": 103,
                    "paymentId": 500,
                    "transactionReference": "TX-1",
                    "amount": 250.00,
                    "timestamp": "2026-10-30T10:00:00Z"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-succeeded-events", 0, 0, "103", json);
        eventListener.onPaymentSucceeded(record);

        verify(inventoryService, times(1)).processPaymentSucceeded(any(PaymentSucceededEvent.class));
    }
}
