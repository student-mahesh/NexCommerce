package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.ProductServiceUnavailableException;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(
                "Jane Doe",
                "456 Market St, San Francisco, CA",
                "SUCCESS",
                List.of(new CreateOrderItemRequest(1L, "Apex Pro Keyboard", "TECH-KB-01", new BigDecimal("129.99"), 1))
        );
    }

    private OrderResponse sampleResponse(Long id, Long customerId) {
        return new OrderResponse(
                id,
                "ORD-" + id,
                customerId,
                "customer@test.com",
                "Jane Doe",
                "456 Market St",
                "SUCCESS",
                new BigDecimal("129.99"),
                OrderStatus.PENDING,
                null,
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should create order successfully when X-User-Id is provided")
    void shouldCreateOrderSuccessfully() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class), eq(5L), any(String.class)))
                .thenReturn(sampleResponse(100L, 5L));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "5")
                        .header("X-User-Email", "jane@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-100"))
                .andExpect(jsonPath("$.data.customerId").value(5));
    }

    @Test
    @DisplayName("Should reject order creation with 401 when X-User-Id is missing")
    void shouldRejectCreateOrderWithoutUserHeader() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required: Missing user identity header"));
    }

    @Test
    @DisplayName("Should reject order creation with 401 when X-User-Id is non-numeric")
    void shouldRejectCreateOrderWithInvalidUserHeader() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should allow customer to view their own order details")
    void shouldAllowCustomerViewOwnOrder() throws Exception {
        when(orderService.getOrderByIdAndCustomer(100L, 5L, false))
                .thenReturn(Optional.of(sampleResponse(100L, 5L)));

        mockMvc.perform(get("/api/orders/100")
                        .header("X-User-Id", "5")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when customer attempts to view another customer's order")
    void shouldForbidCustomerViewingOtherCustomerOrder() throws Exception {
        when(orderService.getOrderByIdAndCustomer(100L, 6L, false))
                .thenReturn(Optional.empty());
        when(orderService.orderExists(100L)).thenReturn(true);

        mockMvc.perform(get("/api/orders/100")
                        .header("X-User-Id", "6")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to view this order"));
    }

    @Test
    @DisplayName("Should allow admin to view any customer's order")
    void shouldAllowAdminViewAnyOrder() throws Exception {
        when(orderService.getOrderByIdAndCustomer(100L, 99L, true))
                .thenReturn(Optional.of(sampleResponse(100L, 5L)));

        mockMvc.perform(get("/api/orders/100")
                        .header("X-User-Id", "99")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("Should return 404 with standard ErrorResponse when order ID does not exist")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(orderService.getOrderByIdAndCustomer(999L, 5L, false))
                .thenReturn(Optional.empty());
        when(orderService.orderExists(999L)).thenReturn(false);

        mockMvc.perform(get("/api/orders/999")
                        .header("X-User-Id", "5")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found with id: 999"))
                .andExpect(jsonPath("$.path").value("/api/orders/999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should reject access to admin orders endpoint if user lacks ROLE_ADMIN")
    void shouldForbidNonAdminAccessToAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/orders/admin")
                        .header("X-User-Id", "5")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));
    }

    @Test
    @DisplayName("Should allow admin to retrieve all orders from admin endpoint")
    void shouldAllowAdminToGetAllOrders() throws Exception {
        when(orderService.getAllOrdersAdmin()).thenReturn(List.of(
                sampleResponse(1L, 10L),
                sampleResponse(2L, 20L)
        ));

        mockMvc.perform(get("/api/orders/admin")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Should retrieve order history for customer on /my-orders")
    void shouldGetMyOrders() throws Exception {
        when(orderService.getCustomerOrders(5L)).thenReturn(List.of(sampleResponse(100L, 5L)));

        mockMvc.perform(get("/api/orders/my-orders")
                        .header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(100));
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable when Product Service dependency is down")
    void shouldReturn503WhenProductServiceUnavailable() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class), eq(5L), any(String.class)))
                .thenThrow(new ProductServiceUnavailableException("Product Service is currently unavailable. Please try again later."));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "5")
                        .header("X-User-Email", "jane@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product Service is currently unavailable. Please try again later."));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when product does not exist in catalog")
    void shouldReturn400WhenProductNotFound() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class), eq(5L), any(String.class)))
                .thenThrow(new IllegalArgumentException("Product not found with id: 999"));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "5")
                        .header("X-User-Email", "jane@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999"));
    }

    @Test
    @DisplayName("Should return 400 with validation errors when CreateOrderRequest is invalid")
    void shouldReturn400OnOrderValidationFailure() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "", // blank name
                "", // blank address
                "SUCCESS",
                List.of() // empty items
        );

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "5")
                        .header("X-User-Email", "jane@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.errors.customerName").isNotEmpty())
                .andExpect(jsonPath("$.errors.shippingAddress").isNotEmpty())
                .andExpect(jsonPath("$.errors.items").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should return 500 sanitized ErrorResponse on unexpected internal error")
    void shouldReturn500SanitizedOnUnexpectedOrderError() throws Exception {
        when(orderService.getCustomerOrders(5L))
                .thenThrow(new RuntimeException("Database deadlock at transaction isolation level"));

        mockMvc.perform(get("/api/orders/my-orders")
                        .header("X-User-Id", "5"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value("/api/orders/my-orders"))
                .andExpect(jsonPath("$.success").value(false));
    }
}

