package com.ecommerce.notification.controller;

import com.ecommerce.notification.dto.ApiResponse;
import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.exception.UnauthorizedException;
import com.ecommerce.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Order notification and audit trail APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get notification audit logs for an order timeline (newest first)")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationsByOrderId(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UnauthorizedException("Authentication required: Missing user identity header");
        }

        Long customerId;
        try {
            customerId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid user identity header format");
        }

        boolean isAdmin = userRolesHeader != null && userRolesHeader.contains("ROLE_ADMIN");
        log.info("Received request for notification audit logs of orderId={}, customerId={}, isAdmin={}",
                orderId, customerId, isAdmin);

        List<NotificationResponse> notifications = notificationService.getNotificationsByOrderId(orderId, customerId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Notification logs retrieved successfully", notifications));
    }
}
