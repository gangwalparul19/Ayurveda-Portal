package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.config.CacheConfig;
import com.ayurveda.platform.tenant.entity.Product;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findAllByIsActiveTrue(Pageable pageable);

    List<Product> findAllByCategory(String category);

    Page<Product> findAllByCategoryAndIsActiveTrue(String category, Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.isActive = true ORDER BY p.category")
    @Cacheable(cacheNames = CacheConfig.PRODUCT_CATEGORIES_CACHE, keyGenerator = "tenantAwareKeyGenerator")
    List<String> findAllActiveCategories();

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchByNameOrSku(@Param("query") String query);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchByNameOrSku(@Param("query") String query, Pageable pageable);

    List<Product> findAllByStockQuantityLessThanEqualAndIsActiveTrue(int threshold);

    /**
     * Find products where stock is at or below their configured low stock threshold.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stockQuantity <= p.lowStockThreshold")
    List<Product> findLowStockProducts();

    /**
     * Find products where stock is at or below their configured low stock threshold (with pagination).
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stockQuantity <= p.lowStockThreshold")
    Page<Product> findLowStockProducts(Pageable pageable);

    /**
     * Find products by active status (for filtering).
     */
    Page<Product> findAllByIsActiveFalse(Pageable pageable);

    /**
     * Find products by category (for storefront)
     */
    Page<Product> findByCategory(String category, Pageable pageable);

    /**
     * Search products by name or description (for storefront)
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchProducts(@Param("query") String query);

    /**
     * Get all distinct categories (for storefront filter)
     */
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.isActive = true ORDER BY p.category")
    @Cacheable(cacheNames = CacheConfig.PRODUCT_CATEGORIES_CACHE, keyGenerator = "tenantAwareKeyGenerator")
    List<String> findDistinctCategories();

    /**
     * Evict the product category cache whenever a product is created, updated,
     * or soft-deleted. All product mutations flow through {@code save}, so a
     * single override keeps the cached category list correct across tenants.
     */
    @Override
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_CATEGORIES_CACHE, allEntries = true)
    <S extends Product> S save(S entity);
}
