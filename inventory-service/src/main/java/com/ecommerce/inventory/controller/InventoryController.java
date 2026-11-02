package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.ApiResponse;
import com.ecommerce.inventory.dto.ReplenishmentRequest;
import com.ecommerce.inventory.entity.InventoryItem;
import com.ecommerce.inventory.exception.ForbiddenException;
import com.ecommerce.inventory.exception.ResourceNotFoundException;
import com.ecommerce.inventory.exception.UnauthorizedException;
import com.ecommerce.inventory.security.JwtUtil;
import com.ecommerce.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock checking, inventory monitoring, and replenishment APIs")
public class InventoryController {

    private final InventoryService inventoryService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get inventory levels for all products")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> getAllStock() {
        log.info("Fetching all inventory stock levels");
        List<InventoryItem> items = inventoryService.getAllStock();
        return ResponseEntity.ok(ApiResponse.ok("Inventory list retrieved successfully", items));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Check available stock for a product ID")
    public ResponseEntity<ApiResponse<InventoryItem>> getStock(@PathVariable Long productId) {
        log.info("Fetching stock level for productId={}", productId);
        return inventoryService.getStockByProductId(productId)
                .map(item -> ResponseEntity.ok(ApiResponse.ok("Stock retrieved successfully", item)))
                .orElseThrow(() -> new ResourceNotFoundException("Product ID " + productId + " not found in inventory"));
    }

    @PostMapping("/{productId}/replenish")
    @Operation(summary = "Replenish available stock for a product ID (Requires Admin)")
    public ResponseEntity<ApiResponse<InventoryItem>> replenishStock(
            @PathVariable Long productId,
            @Valid @RequestBody ReplenishmentRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        log.info("Admin replenishment request for productId={}, quantity={}", productId, request.getQuantity());
        verifyAdminAuthorization(authHeader, userRolesHeader);
        InventoryItem updated = inventoryService.replenishStock(productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.ok("Stock replenished successfully", updated));
    }

    /**
     * Independently verifies cryptographic JWT signature and exact ROLE_ADMIN claim.
     * Prevents privilege escalation and direct port 8083 bypasses via unverified X-User-Roles headers.
     */
    private void verifyAdminAuthorization(String authHeader, String userRolesHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authentication required: Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("Authentication failed: Expired or invalid JWT token");
        }

        String role = jwtUtil.extractRole(token);
        if (!"ROLE_ADMIN".equals(role)) {
            throw new ForbiddenException("Access denied: Admin privileges required");
        }
    }
}
