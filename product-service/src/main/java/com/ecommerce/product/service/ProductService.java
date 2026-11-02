package com.ecommerce.product.service;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Cacheable(
            value = "products",
            key = "'page_' + #page + '_size_' + #size + '_cat_' + (#category != null ? #category : 'all') + '_search_' + (#search != null ? #search : 'none')"
    )
    public PageResponse<ProductResponse> getAllProducts(int page, int size, String category, String search) {
        log.info("Querying products from database: page={}, size={}, category={}, search={}", page, size, category, search);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        String sanitizedCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        String sanitizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<Product> productPage = productRepository.findFilteredProducts(sanitizedCategory, sanitizedSearch, pageable);

        List<ProductResponse> content = productPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return new PageResponse<>(
                content,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isLast()
        );
    }

    @Cacheable(value = "products", key = "'detail_' + #id")
    public ProductResponse getProductById(Long id) {
        log.info("Fetching product details from database for id={}", id);
        return productRepository.findByIdAndActiveTrue(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Transactional
    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating new product with SKU={}", request.sku());

        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product with SKU already exists: " + request.sku());
        }

        Category category = categoryRepository.findByNameIgnoreCase(request.category())
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(request.category().trim())
                        .description(request.category() + " category")
                        .build()));

        Product product = Product.builder()
                .name(request.name().trim())
                .description(request.description().trim())
                .price(request.price())
                .sku(request.sku().trim().toUpperCase())
                .imageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null)
                .category(category)
                .stockQuantity(request.stockQuantity())
                .active(true)
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created successfully with id={}", saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        log.info("Updating product id={}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSku().equalsIgnoreCase(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product with SKU already exists: " + request.sku());
        }

        Category category = categoryRepository.findByNameIgnoreCase(request.category())
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(request.category().trim())
                        .description(request.category() + " category")
                        .build()));

        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        product.setSku(request.sku().trim().toUpperCase());
        product.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : product.getImageUrl());
        product.setCategory(category);
        product.setStockQuantity(request.stockQuantity());

        Product updated = productRepository.save(product);
        log.info("Product updated successfully with id={}", updated.getId());
        return mapToResponse(updated);
    }

    @Transactional
    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public void deleteProduct(Long id) {
        log.info("Soft-deleting product id={}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setActive(false);
        productRepository.save(product);
        log.info("Product id={} soft-deleted successfully", id);
    }

    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        log.info("Fetching all categories from database");
        return categoryRepository.findAll().stream()
                .map(cat -> new CategoryResponse(
                        cat.getId(),
                        cat.getName(),
                        cat.getDescription(),
                        productRepository.countByCategoryId(cat.getId())
                ))
                .toList();
    }

    private ProductResponse mapToResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                product.getImageUrl(),
                product.getCategory().getName(),
                product.getStockQuantity(),
                product.getActive(),
                product.getCreatedAt()
        );
    }
}
