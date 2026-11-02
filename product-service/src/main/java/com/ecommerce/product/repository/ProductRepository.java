package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndActiveTrue(Long id);

    boolean existsBySku(String sku);

    long countByCategoryId(Long categoryId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND (:category IS NULL OR LOWER(p.category.name) = LOWER(:category))
          AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<Product> findFilteredProducts(
            @Param("category") String category,
            @Param("search") String search,
            Pageable pageable
    );
}
