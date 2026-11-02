package com.ecommerce.notification.controller;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.exception.GlobalExceptionHandler;
import com.ecommerce.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 200 OK for valid authenticated customer")
    void testGetNotificationsByOrderId_Success_Customer() throws Exception {
        NotificationResponse res1 = new NotificationResponse(
                1L,
                "evt-1",
                100L,
                5L,
                "user5@test.com",
                "ORDER_CREATED",
                "Order #100 Placed",
                "Order placed successfully",
                "corr-1",
                "RECORDED",
                Instant.now()
        );

        when(notificationService.getNotificationsByOrderId(100L, 5L, false))
                .thenReturn(List.of(res1));

        mockMvc.perform(get("/api/notifications/order/100")
                        .header("X-User-Id", "5")
                        .header("X-User-Email", "user5@test.com")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notification logs retrieved successfully"))
                .andExpect(jsonPath("$.data[0].orderId").value(100))
                .andExpect(jsonPath("$.data[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$.data[0].customerId").value(5))
                .andExpect(jsonPath("$.data[0].eventType").value("ORDER_CREATED"));
    }

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 200 OK for admin user viewing any order")
    void testGetNotificationsByOrderId_Success_Admin() throws Exception {
        NotificationResponse res1 = new NotificationResponse(
                1L,
                "evt-1",
                100L,
                5L,
                "user5@test.com",
                "ORDER_CREATED",
                "Order #100 Placed",
                "Order placed successfully",
                "corr-1",
                "RECORDED",
                Instant.now()
        );

        when(notificationService.getNotificationsByOrderId(100L, 1L, true))
                .thenReturn(List.of(res1));

        mockMvc.perform(get("/api/notifications/order/100")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@ecommerce.com")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].orderId").value(100));
    }

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 403 Forbidden when unauthorized customer accesses another customer order")
    void testGetNotificationsByOrderId_Forbidden() throws Exception {
        when(notificationService.getNotificationsByOrderId(100L, 99L, false))
                .thenThrow(new SecurityException("ACCESS_DENIED"));

        mockMvc.perform(get("/api/notifications/order/100")
                        .header("X-User-Id", "99")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to view notifications for this order"))
                .andExpect(jsonPath("$.path").value("/api/notifications/order/100"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 404 Not Found with standard ErrorResponse when order does not exist")
    void testGetNotificationsByOrderId_NotFound() throws Exception {
        when(notificationService.getNotificationsByOrderId(999L, 5L, false))
                .thenThrow(new IllegalArgumentException("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/notifications/order/999")
                        .header("X-User-Id", "5")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No notifications found for order id: 999"))
                .andExpect(jsonPath("$.path").value("/api/notifications/order/999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 401 Unauthorized when X-User-Id is missing")
    void testGetNotificationsByOrderId_MissingUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/notifications/order/100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required: Missing user identity header"))
                .andExpect(jsonPath("$.path").value("/api/notifications/order/100"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 401 Unauthorized when X-User-Id is invalid format")
    void testGetNotificationsByOrderId_InvalidUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/notifications/order/100")
                        .header("X-User-Id", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid user identity header format"))
                .andExpect(jsonPath("$.path").value("/api/notifications/order/100"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/notifications/order/{orderId} - 500 sanitized ErrorResponse on unexpected error")
    void testGetNotificationsByOrderId_UnexpectedError() throws Exception {
        when(notificationService.getNotificationsByOrderId(100L, 5L, false))
                .thenThrow(new RuntimeException("Kafka producer failed to broadcast notification"));

        mockMvc.perform(get("/api/notifications/order/100")
                        .header("X-User-Id", "5")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value("/api/notifications/order/100"))
                .andExpect(jsonPath("$.success").value(false));
    }
}
