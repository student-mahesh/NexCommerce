package com.ecommerce.product.service;

import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Electronics")
                .description("Electronic gadgets")
                .build();

        testProduct = Product.builder()
                .id(100L)
                .name("Wireless Mouse")
                .description("Ergonomic optical mouse")
                .price(new BigDecimal("29.99"))
                .sku("TEST-MS-01")
                .imageUrl("http://example.com/mouse.jpg")
                .category(testCategory)
                .stockQuantity(15)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should return paginated products successfully")
    void shouldReturnPaginatedProducts() {
        Page<Product> page = new PageImpl<>(List.of(testProduct));
        when(productRepository.findFilteredProducts(any(), any(), any(Pageable.class))).thenReturn(page);

        PageResponse<ProductResponse> result = productService.getAllProducts(0, 10, null, null);

        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().name()).isEqualTo("Wireless Mouse");
        assertThat(result.content().getFirst().sku()).isEqualTo("TEST-MS-01");
    }

    @Test
    @DisplayName("Should return product by ID when product exists")
    void shouldReturnProductByIdWhenExists() {
        when(productRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(testProduct));

        ProductResponse response = productService.getProductById(100L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.name()).isEqualTo("Wireless Mouse");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when product does not exist")
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should create new product successfully")
    void shouldCreateProductSuccessfully() {
        ProductRequest request = new ProductRequest(
                "Gaming Keyboard",
                "RGB mechanical",
                new BigDecimal("89.99"),
                "TEST-KB-02",
                "http://example.com/kb.jpg",
                "Electronics",
                20
        );

        when(productRepository.existsBySku("TEST-KB-02")).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Electronics")).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(101L);
            p.setCreatedAt(Instant.now());
            return p;
        });

        ProductResponse created = productService.createProduct(request);

        assertThat(created).isNotNull();
        assertThat(created.sku()).isEqualTo("TEST-KB-02");
        assertThat(created.name()).isEqualTo("Gaming Keyboard");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when creating product with existing SKU")
    void shouldThrowOnDuplicateSku() {
        ProductRequest request = new ProductRequest(
                "Gaming Keyboard",
                "RGB mechanical",
                new BigDecimal("89.99"),
                "TEST-MS-01",
                "http://example.com/kb.jpg",
                "Electronics",
                20
        );

        when(productRepository.existsBySku("TEST-MS-01")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("TEST-MS-01");

        verify(productRepository, never()).save(any(Product.class));
    }
}
