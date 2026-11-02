package com.ecommerce.product.controller;

import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.security.JwtUtil;
import com.ecommerce.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtUtil jwtUtil;

    private static final String VALID_ADMIN_TOKEN = "valid.admin.jwt.token";
    private static final String VALID_CUSTOMER_TOKEN = "valid.customer.jwt.token";
    private static final String INVALID_TOKEN = "invalid.tampered.token";

    private ProductRequest sampleRequest() {
        return new ProductRequest(
                "Apex Pro Keyboard",
                "Flagship mechanical gaming keyboard",
                new BigDecimal("129.99"),
                "TECH-KB-01",
                "http://example.com/keyboard.jpg",
                "Electronics",
                50
        );
    }

    private ProductResponse sampleResponse(Long id) {
        return new ProductResponse(
                id,
                "Apex Pro Keyboard",
                "Flagship mechanical gaming keyboard",
                new BigDecimal("129.99"),
                "TECH-KB-01",
                "http://example.com/keyboard.jpg",
                "Electronics",
                50,
                true,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should allow public GET /api/products without authentication header")
    void shouldAllowPublicGetProducts() throws Exception {
        when(productService.getAllProducts(0, 12, null, null))
                .thenReturn(new PageResponse<>(List.of(sampleResponse(1L)), 0, 12, 1L, 1, true));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Apex Pro Keyboard"));
    }

    @Test
    @DisplayName("Should allow public GET /api/products/{id} without authentication header")
    void shouldAllowPublicGetProductById() throws Exception {
        when(productService.getProductById(1L)).thenReturn(sampleResponse(1L));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("Should allow POST /api/products when caller provides valid Admin JWT")
    void shouldAllowCreateProductWithAdminJwt() throws Exception {
        when(jwtUtil.validateToken(VALID_ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(sampleResponse(10L));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(productService, times(1)).createProduct(any(ProductRequest.class));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized on POST /api/products when Authorization header is missing")
    void shouldRejectCreateProductWithoutAuthHeader() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required: Missing or invalid Authorization header"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized on direct POST /api/products when caller spoofs X-User-Roles without token")
    void shouldRejectCreateProductWithSpoofedRoleHeaderWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "attacker@evil.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required: Missing or invalid Authorization header"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized on POST /api/products when JWT token is invalid or tampered")
    void shouldRejectCreateProductWithInvalidJwt() throws Exception {
        when(jwtUtil.validateToken(INVALID_TOKEN)).thenReturn(false);

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Expired or invalid JWT token"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should return 403 Forbidden on POST /api/products when caller provides Customer JWT")
    void shouldRejectCreateProductWithCustomerJwt() throws Exception {
        when(jwtUtil.validateToken(VALID_CUSTOMER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_CUSTOMER_TOKEN)).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_CUSTOMER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should return 403 Forbidden when caller provides Customer JWT even if spoofing X-User-Roles: ROLE_ADMIN")
    void shouldRejectCreateProductWithCustomerJwtEvenWithSpoofedAdminHeader() throws Exception {
        when(jwtUtil.validateToken(VALID_CUSTOMER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_CUSTOMER_TOKEN)).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_CUSTOMER_TOKEN)
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should allow PUT /api/products/{id} when caller provides valid Admin JWT")
    void shouldAllowUpdateProductWithAdminJwt() throws Exception {
        when(jwtUtil.validateToken(VALID_ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");
        when(productService.updateProduct(eq(1L), any(ProductRequest.class))).thenReturn(sampleResponse(1L));

        mockMvc.perform(put("/api/products/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(productService, times(1)).updateProduct(eq(1L), any(ProductRequest.class));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized on direct PUT /api/products/{id} without token")
    void shouldRejectUpdateProductWithoutToken() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(productService, never()).updateProduct(any(), any());
    }

    @Test
    @DisplayName("Should return 403 Forbidden on PUT /api/products/{id} when caller provides Customer JWT")
    void shouldRejectUpdateProductWithCustomerJwt() throws Exception {
        when(jwtUtil.validateToken(VALID_CUSTOMER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_CUSTOMER_TOKEN)).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(put("/api/products/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_CUSTOMER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));

        verify(productService, never()).updateProduct(any(), any());
    }

    @Test
    @DisplayName("Should allow DELETE /api/products/{id} when caller provides valid Admin JWT")
    void shouldAllowDeleteProductWithAdminJwt() throws Exception {
        when(jwtUtil.validateToken(VALID_ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product deleted successfully"));

        verify(productService, times(1)).deleteProduct(1L);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized on direct DELETE /api/products/{id} without token")
    void shouldRejectDeleteProductWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/products/1")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(productService, never()).deleteProduct(any());
    }

    @Test
    @DisplayName("Should return 403 Forbidden on DELETE /api/products/{id} when caller provides Customer JWT")
    void shouldRejectDeleteProductWithCustomerJwt() throws Exception {
        when(jwtUtil.validateToken(VALID_CUSTOMER_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_CUSTOMER_TOKEN)).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(delete("/api/products/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_CUSTOMER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));

        verify(productService, never()).deleteProduct(any());
    }

    @Test
    @DisplayName("Should return 404 with standard ErrorResponse when product is not found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(productService.getProductById(999L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 999"));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999"))
                .andExpect(jsonPath("$.path").value("/api/products/999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should return 400 with validation errors map when request validation fails")
    void shouldReturn400OnValidationFailure() throws Exception {
        when(jwtUtil.validateToken(VALID_ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");

        ProductRequest invalidRequest = new ProductRequest(
                "",
                "A description",
                new BigDecimal("-10.00"),
                "INVALID_SKU",
                "http://example.com/test.jpg",
                "Electronics",
                -5
        );

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should return 409 Conflict when creating product with duplicate SKU")
    void shouldReturn409OnDuplicateSku() throws Exception {
        when(jwtUtil.validateToken(VALID_ADMIN_TOKEN)).thenReturn(true);
        when(jwtUtil.extractRole(VALID_ADMIN_TOKEN)).thenReturn("ROLE_ADMIN");
        when(productService.createProduct(any(ProductRequest.class)))
                .thenThrow(new DuplicateResourceException("Product with SKU TECH-KB-01 already exists"));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Product with SKU TECH-KB-01 already exists"))
                .andExpect(jsonPath("$.path").value("/api/products"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should return 500 sanitized ErrorResponse without leaking internal exception details")
    void shouldReturn500SanitizedOnUnexpectedException() throws Exception {
        when(productService.getProductById(1L))
                .thenThrow(new RuntimeException("SQL syntax error near SELECT * FROM sensitive_table"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value("/api/products/1"))
                .andExpect(jsonPath("$.success").value(false));
    }
}
