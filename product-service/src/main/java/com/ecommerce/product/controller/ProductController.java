package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ApiResponse;
import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.exception.ForbiddenException;
import com.ecommerce.product.exception.UnauthorizedException;
import com.ecommerce.product.security.JwtUtil;
import com.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product Catalog Management APIs")
public class ProductController {

    private final ProductService productService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get paginated products with category and search filters")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search
    ) {
        log.info("Fetching products page={}, size={}, category={}, search={}", page, size, category, search);
        PageResponse<ProductResponse> products = productService.getAllProducts(page, size, category, search);
        return ResponseEntity.ok(ApiResponse.ok("Products retrieved successfully", products));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single product by ID (Cached in Redis)")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        log.info("Fetching product id={}", id);
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.ok("Product retrieved successfully", product));
    }

    @PostMapping
    @Operation(summary = "Create a new product (Evicts Redis cache, Requires Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        log.info("Admin request to create product: name={}, sku={}", request.name(), request.sku());
        verifyAdminAuthorization(authHeader, userRolesHeader);
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created successfully", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product (Evicts Redis cache, Requires Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        log.info("Admin request to update product id={}: name={}, sku={}", id, request.name(), request.sku());
        verifyAdminAuthorization(authHeader, userRolesHeader);
        ProductResponse updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Product updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a product by ID (Evicts Redis cache, Requires Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader
    ) {
        log.info("Admin request to delete product id={}", id);
        verifyAdminAuthorization(authHeader, userRolesHeader);
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted successfully", null));
    }

    /**
     * Independently verifies cryptographic JWT signature and exact ROLE_ADMIN claim.
     * Prevents privilege escalation and direct port 8081 bypasses via unverified X-User-Roles headers.
     */
    private void verifyAdminAuthorization(String authHeader, String userRolesHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authentication required: Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("Expired or invalid JWT token");
        }

        String tokenRole = jwtUtil.extractRole(token);
        if (!"ROLE_ADMIN".equals(tokenRole)) {
            throw new ForbiddenException("Access denied: Admin role required");
        }
    }
}
