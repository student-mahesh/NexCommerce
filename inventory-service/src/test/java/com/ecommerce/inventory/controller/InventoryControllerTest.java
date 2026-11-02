package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.ReplenishmentRequest;
import com.ecommerce.inventory.entity.InventoryItem;
import com.ecommerce.inventory.exception.GlobalExceptionHandler;
import com.ecommerce.inventory.exception.InsufficientInventoryException;
import com.ecommerce.inventory.exception.ResourceNotFoundException;
import com.ecommerce.inventory.security.JwtUtil;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private JwtUtil jwtUtil;

    private static final String ADMIN_TOKEN = "valid.admin.jwt";
    private static final String CUSTOMER_TOKEN = "valid.customer.jwt";
    private static final String EXPIRED_TOKEN = "expired.jwt";

    @Test
    @DisplayName("GET /api/inventory/{productId} - Should return 200 OK with stock details when product exists")
    void shouldReturnStockWhenProductExists() throws Exception {
        InventoryItem item = InventoryItem.builder()
                .id(1L)
                .productId(1L)
                .sku("TECH-KB-01")
                .availableQuantity(45)
                .reservedQuantity(5)
                .build();

        when(inventoryService.getStockByProductId(1L)).thenReturn(Optional.of(item));

        mockMvc.perform(get("/api/inventory/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.sku").value("TECH-KB-01"))
                .andExpect(jsonPath("$.data.availableQuantity").value(45))
                .andExpect(jsonPath("$.data.reservedQuantity").value(5));
    }

    @Test
    @DisplayName("GET /api/inventory/{productId} - Should return 404 Not Found when product does not exist")
    void shouldReturn404WhenProductDoesNotExist() throws Exception {
        when(inventoryService.getStockByProductId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/inventory - Should return 200 OK with list of all inventory items")
    void shouldReturnAllInventoryItems() throws Exception {
        List<InventoryItem> items = List.of(
                InventoryItem.builder().id(1L).productId(1L).sku("TECH-KB-01").availableQuantity(45).reservedQuantity(0).build(),
                InventoryItem.builder().id(2L).productId(2L).sku("AUD-HP-02").availableQuantity(30).reservedQuantity(0).build()
        );

        when(inventoryService.getAllStock()).thenReturn(items);

        mockMvc.perform(get("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productId").value(1))
                .andExpect(jsonPath("$.data[1].productId").value(2));
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should succeed with 200 OK for valid admin JWT")
    void shouldReplenishStockSuccessfullyForAdmin() throws Exception {
        when(jwtUtil.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        InventoryItem replenished = InventoryItem.builder()
                .id(1L)
                .productId(1L)
                .sku("TECH-KB-01")
                .availableQuantity(95)
                .reservedQuantity(5)
                .build();

        when(inventoryService.replenishStock(1L, 50)).thenReturn(replenished);

        ReplenishmentRequest request = new ReplenishmentRequest(50);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.availableQuantity").value(95))
                .andExpect(jsonPath("$.data.reservedQuantity").value(5));
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 401 when Authorization header is missing")
    void shouldReturn401WhenAuthHeaderMissing() throws Exception {
        ReplenishmentRequest request = new ReplenishmentRequest(20);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required: Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 401 when JWT is invalid or expired")
    void shouldReturn401WhenTokenInvalidOrExpired() throws Exception {
        when(jwtUtil.validateToken(EXPIRED_TOKEN)).thenReturn(false);

        ReplenishmentRequest request = new ReplenishmentRequest(20);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed: Expired or invalid JWT token"));
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 403 Forbidden for non-admin customer JWT")
    void shouldReturn403ForCustomerToken() throws Exception {
        when(jwtUtil.validateToken(CUSTOMER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(CUSTOMER_TOKEN)).thenReturn("ROLE_CUSTOMER");

        ReplenishmentRequest request = new ReplenishmentRequest(20);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CUSTOMER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin privileges required"));
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should reject spoofed X-User-Roles header without valid JWT")
    void shouldRejectSpoofedAdminHeaderWithoutJwt() throws Exception {
        ReplenishmentRequest request = new ReplenishmentRequest(20);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 400 Bad Request for zero quantity")
    void shouldReturn400ForZeroQuantity() throws Exception {
        when(jwtUtil.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        ReplenishmentRequest request = new ReplenishmentRequest(0);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 400 Bad Request for negative quantity")
    void shouldReturn400ForNegativeQuantity() throws Exception {
        when(jwtUtil.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        ReplenishmentRequest request = new ReplenishmentRequest(-5);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 400 Bad Request for quantity > 10,000")
    void shouldReturn400ForExcessiveQuantity() throws Exception {
        when(jwtUtil.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        ReplenishmentRequest request = new ReplenishmentRequest(10001);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 404 Not Found for non-existent product ID")
    void shouldReturn404ForUnknownProductId() throws Exception {
        when(jwtUtil.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        when(inventoryService.replenishStock(eq(9999L), any(Integer.class)))
                .thenThrow(new ResourceNotFoundException("Product ID 9999 not found in inventory"));

        ReplenishmentRequest request = new ReplenishmentRequest(10);

        mockMvc.perform(post("/api/inventory/9999/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product ID 9999 not found in inventory"));
    }

    @Test
    @DisplayName("GET /api/inventory/{productId} - Should return 404 with standard ErrorResponse when item not found")
    void shouldReturn404StandardErrorResponseWhenItemNotFound() throws Exception {
        when(inventoryService.getStockByProductId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product ID 999 not found in inventory"))
                .andExpect(jsonPath("$.path").value("/api/inventory/999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/inventory/{productId}/replenish - Should return 409 Conflict when InsufficientInventoryException occurs")
    void shouldReturn409OnInsufficientInventory() throws Exception {
        when(jwtUtil.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        when(inventoryService.replenishStock(eq(1L), any(Integer.class)))
                .thenThrow(new InsufficientInventoryException("Cannot adjust stock: reservation conflict"));

        ReplenishmentRequest request = new ReplenishmentRequest(10);

        mockMvc.perform(post("/api/inventory/1/replenish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot adjust stock: reservation conflict"))
                .andExpect(jsonPath("$.path").value("/api/inventory/1/replenish"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/inventory - Should return 500 sanitized ErrorResponse on unexpected database error")
    void shouldReturn500SanitizedOnUnexpectedInventoryError() throws Exception {
        when(inventoryService.getAllStock())
                .thenThrow(new RuntimeException("HikariPool connection leak: failed to acquire connection"));

        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value("/api/inventory"))
                .andExpect(jsonPath("$.success").value(false));
    }
}
