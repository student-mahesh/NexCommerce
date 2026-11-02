package com.ecommerce.notification.service;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.entity.NotificationLog;
import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("Should successfully record notification log")
    void testRecordNotificationSuccess() {
        NotificationLog mockLog = NotificationLog.builder()
                .id(1L)
                .eventId("evt-101")
                .orderId(101L)
                .customerId(5L)
                .recipientEmail("customer@test.com")
                .type(NotificationType.ORDER_CREATED)
                .subject("Order #101 Placed")
                .message("Order placed successfully")
                .correlationId("corr-101")
                .status("RECORDED")
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.existsByEventId("evt-101")).thenReturn(false);
        when(notificationRepository.save(any(NotificationLog.class))).thenReturn(mockLog);

        Optional<NotificationLog> savedOpt = notificationService.recordNotification(
                "evt-101",
                101L,
                5L,
                "customer@test.com",
                NotificationType.ORDER_CREATED,
                "Order #101 Placed",
                "Order placed successfully",
                "corr-101"
        );

        assertTrue(savedOpt.isPresent());
        NotificationLog saved = savedOpt.get();
        assertEquals(101L, saved.getOrderId());
        assertEquals("evt-101", saved.getEventId());
        assertEquals(5L, saved.getCustomerId());
        assertEquals(NotificationType.ORDER_CREATED, saved.getType());
        assertEquals("RECORDED", saved.getStatus());
        verify(notificationRepository, times(1)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should skip duplicate event when existsByEventId returns true")
    void testRecordNotificationIdempotencyByEventId() {
        when(notificationRepository.existsByEventId("evt-dup")).thenReturn(true);

        Optional<NotificationLog> result = notificationService.recordNotification(
                "evt-dup",
                102L,
                5L,
                "customer@test.com",
                NotificationType.INVENTORY_RESERVED,
                "Items Reserved",
                "Stock reserved",
                "corr-dup"
        );

        assertFalse(result.isPresent());
        verify(notificationRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should handle concurrent duplicate race conditions when unique constraint fails")
    void testRecordNotificationIdempotencyByUniqueConstraint() {
        when(notificationRepository.existsByEventId("evt-race")).thenReturn(false);
        when(notificationRepository.save(any(NotificationLog.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'evt-race' for key 'uk_notification_event_id'"));

        Optional<NotificationLog> result = notificationService.recordNotification(
                "evt-race",
                103L,
                5L,
                "customer@test.com",
                NotificationType.PAYMENT_SUCCEEDED,
                "Payment Confirmed",
                "Payment authorized",
                "corr-race"
        );

        assertFalse(result.isPresent());
        verify(notificationRepository, times(1)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should return empty when orderId is null")
    void testRecordNotificationNullOrderId() {
        Optional<NotificationLog> result = notificationService.recordNotification(
                "evt-null",
                null,
                5L,
                "customer@test.com",
                NotificationType.ORDER_CREATED,
                "Sub",
                "Msg",
                "corr-1"
        );

        assertFalse(result.isPresent());
        verify(notificationRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should generate fallback eventId when eventId is null or blank")
    void testRecordNotificationFallbackEventId() {
        when(notificationRepository.existsByEventId("order-104-ORDER_CREATED")).thenReturn(false);
        when(notificationRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        Optional<NotificationLog> result = notificationService.recordNotification(
                null,
                104L,
                7L,
                "cust@test.com",
                NotificationType.ORDER_CREATED,
                "Subject",
                "Message",
                "corr-fb"
        );

        assertTrue(result.isPresent());
        assertEquals("order-104-ORDER_CREATED", result.get().getEventId());
    }

    @Test
    @DisplayName("Should resolve customerId from previous order notification when missing in current event")
    void testRecordNotificationCustomerResolution() {
        NotificationLog prior = NotificationLog.builder()
                .orderId(105L)
                .customerId(9L)
                .recipientEmail("user9@test.com")
                .build();

        when(notificationRepository.existsByEventId("evt-pay")).thenReturn(false);
        when(notificationRepository.findFirstByOrderIdAndCustomerIdIsNotNull(105L)).thenReturn(Optional.of(prior));
        when(notificationRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        Optional<NotificationLog> result = notificationService.recordNotification(
                "evt-pay",
                105L,
                null, // customerId missing in payment event
                null, // email missing in payment event
                NotificationType.PAYMENT_SUCCEEDED,
                "Payment Confirmed",
                "Authorized",
                "corr-pay"
        );

        assertTrue(result.isPresent());
        assertEquals(9L, result.get().getCustomerId());
        assertEquals("user9@test.com", result.get().getRecipientEmail());
    }

    @Test
    @DisplayName("Should verify notification message creation for all 5 event types")
    void testAllFiveNotificationTypes() {
        NotificationType[] types = {
                NotificationType.ORDER_CREATED,
                NotificationType.INVENTORY_RESERVED,
                NotificationType.INVENTORY_REJECTED,
                NotificationType.PAYMENT_SUCCEEDED,
                NotificationType.PAYMENT_FAILED
        };

        for (NotificationType type : types) {
            String eventId = "evt-" + type.name();
            when(notificationRepository.existsByEventId(eventId)).thenReturn(false);
            when(notificationRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

            Optional<NotificationLog> result = notificationService.recordNotification(
                    eventId,
                    200L,
                    1L,
                    "test@test.com",
                    type,
                    "Subject " + type.name(),
                    "Message " + type.name(),
                    "corr-" + type.name()
            );

            assertTrue(result.isPresent());
            assertEquals(type, result.get().getType());
        }
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when order does not exist")
    void testGetNotificationsByOrderId_OrderNotFound() {
        when(notificationRepository.existsByOrderId(999L)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                notificationService.getNotificationsByOrderId(999L, 1L, false));

        assertEquals("ORDER_NOT_FOUND", ex.getMessage());
    }

    @Test
    @DisplayName("Should return notification DTOs when customer owns the order")
    void testGetNotificationsByOrderId_CustomerOwnershipMatch() {
        NotificationLog log1 = NotificationLog.builder()
                .id(1L)
                .eventId("evt-1")
                .orderId(101L)
                .customerId(5L)
                .recipientEmail("user5@test.com")
                .type(NotificationType.ORDER_CREATED)
                .subject("Placed")
                .message("Order placed")
                .correlationId("corr-1")
                .status("RECORDED")
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.existsByOrderId(101L)).thenReturn(true);
        when(notificationRepository.findByOrderIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(log1));

        List<NotificationResponse> responses = notificationService.getNotificationsByOrderId(101L, 5L, false);

        assertEquals(1, responses.size());
        assertEquals("evt-1", responses.get(0).eventId());
        assertEquals(5L, responses.get(0).customerId());
        assertEquals("ORDER_CREATED", responses.get(0).eventType());
    }

    @Test
    @DisplayName("Should throw SecurityException when customer does not own the order")
    void testGetNotificationsByOrderId_CustomerOwnershipForbidden() {
        NotificationLog log1 = NotificationLog.builder()
                .id(1L)
                .orderId(101L)
                .customerId(5L) // Owned by customer 5
                .type(NotificationType.ORDER_CREATED)
                .build();

        when(notificationRepository.existsByOrderId(101L)).thenReturn(true);
        when(notificationRepository.findByOrderIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(log1));

        // Requesting customer is customer 99
        SecurityException ex = assertThrows(SecurityException.class, () ->
                notificationService.getNotificationsByOrderId(101L, 99L, false));

        assertEquals("ACCESS_DENIED", ex.getMessage());
    }

    @Test
    @DisplayName("Should allow admin to view notifications even if customerId differs")
    void testGetNotificationsByOrderId_AdminBypass() {
        NotificationLog log1 = NotificationLog.builder()
                .id(1L)
                .eventId("evt-admin")
                .orderId(101L)
                .customerId(5L) // Owned by customer 5
                .type(NotificationType.ORDER_CREATED)
                .subject("Order Placed")
                .message("Msg")
                .status("RECORDED")
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.existsByOrderId(101L)).thenReturn(true);
        when(notificationRepository.findByOrderIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(log1));

        // Requesting user is admin (ID 1, isAdmin = true)
        List<NotificationResponse> responses = notificationService.getNotificationsByOrderId(101L, 1L, true);

        assertEquals(1, responses.size());
        assertEquals("evt-admin", responses.get(0).eventId());
    }
}
