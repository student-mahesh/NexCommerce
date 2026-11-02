package com.ecommerce.order.controller;

import com.ecommerce.order.dto.ApiResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.ForbiddenException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management, checkout, and customer order history APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new order and trigger the Event-Driven Saga workflow")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Email", required = false) String userEmailHeader
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

        String customerEmail = (userEmailHeader != null && !userEmailHeader.isBlank())
                ? userEmailHeader.trim()
                : "user" + customerId + "@ecommerce.com";

        log.info("Order placement request received for customerId={}, email={}, itemsCount={}",
                customerId, customerEmail, request != null && request.items() != null ? request.items().size() : 0);

        OrderResponse orderResponse = orderService.createOrder(request, customerId, customerEmail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order placed successfully. Saga initiated.", orderResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details and real-time Saga status by order ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UnauthorizedException("Authentication required to view order details");
        }

        Long customerId;
        try {
            customerId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid user identity header format");
        }

        boolean isAdmin = userRolesHeader != null && userRolesHeader.contains("ROLE_ADMIN");
        log.info("Fetching order id={} for customerId={}, isAdmin={}", id, customerId, isAdmin);

        return orderService.getOrderByIdAndCustomer(id, customerId, isAdmin)
                .map(order -> ResponseEntity.ok(ApiResponse.ok("Order retrieved successfully", order)))
                .orElseThrow(() -> {
                    if (orderService.orderExists(id) && !isAdmin) {
                        return new ForbiddenException("Access denied: You do not have permission to view this order");
                    }
                    return new ResourceNotFoundException("Order not found with id: " + id);
                });
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get order history for the authenticated customer")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader
    ) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UnauthorizedException("Authentication required to view order history");
        }

        Long customerId;
        try {
            customerId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid user identity header format");
        }

        log.info("Fetching order history for customerId={}", customerId);
        List<OrderResponse> orders = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(ApiResponse.ok("Customer orders retrieved successfully", orders));
    }

    @GetMapping("/admin")
    @Operation(summary = "Get all system orders for admin tracking")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrdersAdmin(
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        if (userRolesHeader == null || !userRolesHeader.contains("ROLE_ADMIN")) {
            throw new ForbiddenException("Access denied: Admin role required");
        }

        log.info("Admin fetching all system orders across platform");
        List<OrderResponse> orders = orderService.getAllOrdersAdmin();
        return ResponseEntity.ok(ApiResponse.ok("All orders retrieved successfully", orders));
    }
}
