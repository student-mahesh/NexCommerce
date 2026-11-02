package com.ecommerce.notification.kafka;

import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    private NotificationEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new NotificationEventListener(notificationService, objectMapper);
    }

    @Test
    @DisplayName("Should successfully consume OrderCreatedEvent and acknowledge offset")
    void testOnOrderCreated_Success() {
        String payload = """
                {
                    "eventId": "evt-ord-1",
                    "correlationId": "corr-ord-1",
                    "eventType": "ORDER_CREATED",
                    "orderId": 501,
                    "customerId": 10,
                    "customerEmail": "user10@test.com",
                    "totalAmount": 199.98,
                    "paymentSimulateMode": "SUCCESS",
                    "items": [
                        {"productId": 1, "sku": "KB-01", "quantity": 2, "unitPrice": 99.99}
                    ],
                    "timestamp": "2026-10-30T10:00:00Z"
                }
                """;

        when(notificationService.recordNotification(
                eq("evt-ord-1"), eq(501L), eq(10L), eq("user10@test.com"),
                eq(NotificationType.ORDER_CREATED), any(), any(), eq("corr-ord-1")
        )).thenReturn(Optional.empty());

        listener.onOrderCreated(payload, acknowledgment);

        verify(notificationService, times(1)).recordNotification(
                eq("evt-ord-1"), eq(501L), eq(10L), eq("user10@test.com"),
                eq(NotificationType.ORDER_CREATED), any(), any(), eq("corr-ord-1")
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should discard malformed OrderCreatedEvent with missing orderId and acknowledge")
    void testOnOrderCreated_MissingOrderId() {
        String payload = """
                {
                    "eventId": "evt-ord-2",
                    "eventType": "ORDER_CREATED"
                }
                """;

        listener.onOrderCreated(payload, acknowledgment);

        verify(notificationService, never()).recordNotification(any(), any(), any(), any(), any(), any(), any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should discard poison pill JSON and acknowledge offset to prevent loop")
    void testOnOrderCreated_PoisonPillJson() {
        String payload = "{ malformed JSON text without quotes : ";

        listener.onOrderCreated(payload, acknowledgment);

        verify(notificationService, never()).recordNotification(any(), any(), any(), any(), any(), any(), any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should NOT acknowledge offset when database write fails with DataAccessException so Kafka retries")
    void testOnOrderCreated_DatabaseFailure_DoesNotAcknowledge() {
        String payload = """
                {
                    "eventId": "evt-ord-fail-db",
                    "correlationId": "corr-ord-fail",
                    "eventType": "ORDER_CREATED",
                    "orderId": 501,
                    "customerId": 10,
                    "customerEmail": "user10@test.com",
                    "totalAmount": 199.98,
                    "paymentSimulateMode": "SUCCESS",
                    "items": [
                        {"productId": 1, "sku": "KB-01", "quantity": 2, "unitPrice": 99.99}
                    ],
                    "timestamp": "2026-10-30T10:00:00Z"
                }
                """;

        when(notificationService.recordNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("MySQL database unreachable"));

        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                listener.onOrderCreated(payload, acknowledgment)
        );

        // Acknowledgment must NOT be called, ensuring Kafka leaves the offset uncommitted for redelivery
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should successfully consume InventoryReservedEvent and acknowledge offset")
    void testOnInventoryReserved_Success() {
        String payload = """
                {
                    "eventId": "evt-inv-res-1",
                    "correlationId": "corr-inv-1",
                    "eventType": "INVENTORY_RESERVED",
                    "orderId": 502,
                    "customerId": 11,
                    "customerEmail": "user11@test.com",
                    "totalAmount": 89.99,
                    "paymentSimulateMode": "SUCCESS",
                    "reservedItems": [{"productId": 1, "quantity": 1}],
                    "timestamp": "2026-10-30T10:05:00Z"
                }
                """;

        when(notificationService.recordNotification(
                eq("evt-inv-res-1"), eq(502L), eq(11L), eq("user11@test.com"),
                eq(NotificationType.INVENTORY_RESERVED), any(), any(), eq("corr-inv-1")
        )).thenReturn(Optional.empty());

        listener.onInventoryReserved(payload, acknowledgment);

        verify(notificationService, times(1)).recordNotification(
                eq("evt-inv-res-1"), eq(502L), eq(11L), eq("user11@test.com"),
                eq(NotificationType.INVENTORY_RESERVED), any(), any(), eq("corr-inv-1")
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should successfully consume InventoryRejectedEvent and acknowledge offset")
    void testOnInventoryRejected_Success() {
        String payload = """
                {
                    "eventId": "evt-inv-rej-1",
                    "correlationId": "corr-inv-2",
                    "eventType": "INVENTORY_REJECTED",
                    "orderId": 503,
                    "reason": "Insufficient stock for product ID 1 (available: 0, requested: 5)",
                    "timestamp": "2026-10-30T10:10:00Z"
                }
                """;

        when(notificationService.recordNotification(
                eq("evt-inv-rej-1"), eq(503L), isNull(), isNull(),
                eq(NotificationType.INVENTORY_REJECTED), any(), any(), eq("corr-inv-2")
        )).thenReturn(Optional.empty());

        listener.onInventoryRejected(payload, acknowledgment);

        verify(notificationService, times(1)).recordNotification(
                eq("evt-inv-rej-1"), eq(503L), isNull(), isNull(),
                eq(NotificationType.INVENTORY_REJECTED), any(), any(), eq("corr-inv-2")
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should successfully consume PaymentSucceededEvent and acknowledge offset")
    void testOnPaymentSucceeded_Success() {
        String payload = """
                {
                    "eventId": "evt-pay-succ-1",
                    "correlationId": "corr-pay-1",
                    "eventType": "PaymentSucceededEvent",
                    "orderId": 504,
                    "paymentId": 801,
                    "transactionReference": "TXN-TEST-12345",
                    "amount": 249.99,
                    "timestamp": "2026-10-30T10:15:00Z"
                }
                """;

        when(notificationService.recordNotification(
                eq("evt-pay-succ-1"), eq(504L), isNull(), isNull(),
                eq(NotificationType.PAYMENT_SUCCEEDED), any(), any(), eq("corr-pay-1")
        )).thenReturn(Optional.empty());

        listener.onPaymentSucceeded(payload, acknowledgment);

        verify(notificationService, times(1)).recordNotification(
                eq("evt-pay-succ-1"), eq(504L), isNull(), isNull(),
                eq(NotificationType.PAYMENT_SUCCEEDED), any(), any(), eq("corr-pay-1")
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should successfully consume PaymentFailedEvent and acknowledge offset")
    void testOnPaymentFailed_Success() {
        String payload = """
                {
                    "eventId": "evt-pay-fail-1",
                    "correlationId": "corr-pay-2",
                    "eventType": "PaymentFailedEvent",
                    "orderId": 505,
                    "reason": "Simulated card decline",
                    "timestamp": "2026-10-30T10:20:00Z"
                }
                """;

        when(notificationService.recordNotification(
                eq("evt-pay-fail-1"), eq(505L), isNull(), isNull(),
                eq(NotificationType.PAYMENT_FAILED), any(), any(), eq("corr-pay-2")
        )).thenReturn(Optional.empty());

        listener.onPaymentFailed(payload, acknowledgment);

        verify(notificationService, times(1)).recordNotification(
                eq("evt-pay-fail-1"), eq(505L), isNull(), isNull(),
                eq(NotificationType.PAYMENT_FAILED), any(), any(), eq("corr-pay-2")
        );
        verify(acknowledgment, times(1)).acknowledge();
    }
}
