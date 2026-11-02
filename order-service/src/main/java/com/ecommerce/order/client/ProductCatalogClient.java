package com.ecommerce.order.client;

import com.ecommerce.order.exception.ProductServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * HTTP Client to query the Product Service for authoritative catalog data,
 * enforcing fail-closed validation to prevent price tampering or invalid orders.
 */
@Component
public class ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);

    private final RestClient restClient;

    public ProductCatalogClient(
            @Value("${product-service.url:http://${PRODUCT_SERVICE_HOST:localhost}:${PRODUCT_SERVICE_PORT:8081}}") String productServiceUrl
    ) {
        log.info("Initialized ProductCatalogClient with baseUrl: {}", productServiceUrl);
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    /**
     * Queries the Product Service for a product by ID.
     *
     * @param productId product identifier
     * @return Optional containing the CatalogProductDto if found, or empty if 404 Not Found
     * @throws ProductServiceUnavailableException if Product Service is unreachable, times out, or returns an error
     */
    public Optional<CatalogProductDto> getProduct(Long productId) {
        if (productId == null) return Optional.empty();
        try {
            CatalogApiResponse response = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CatalogApiResponse.class);

            if (response != null && response.data() != null) {
                return Optional.of(response.data());
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Product not found in catalog for productId={}", productId);
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("Product not found in catalog for productId={}", productId);
                return Optional.empty();
            }
            log.error("Product service returned client error for productId={}: {}", productId, e.getMessage());
            throw new ProductServiceUnavailableException("Product Service returned error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Product service is unavailable or timed out for productId={}: {}", productId, e.getMessage());
            throw new ProductServiceUnavailableException("Product Service is currently unavailable. Please try again later.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogApiResponse(boolean success, String message, CatalogProductDto data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogProductDto(Long id, String name, String sku, BigDecimal price, Boolean active) {}
}
