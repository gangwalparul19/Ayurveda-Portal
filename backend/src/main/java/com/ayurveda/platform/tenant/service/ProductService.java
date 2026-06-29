package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for product management within a tenant's database.
 * All operations are scoped to the current tenant via TenantContext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public Page<Product> getAllActiveProducts(Pageable pageable) {
        return productRepository.findAllByIsActiveTrue(pageable);
    }

    public Page<Product> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findAllByCategoryAndIsActiveTrue(category, pageable);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    public Product getProductBySku(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
    }

    public List<Product> searchProducts(String query) {
        return productRepository.searchByNameOrSku(query);
    }

    public List<String> getAllCategories() {
        return productRepository.findAllActiveCategories();
    }

    @Transactional
    public Product createProduct(Product product) {
        if (productRepository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("Product with SKU already exists: " + product.getSku());
        }
        Product saved = productRepository.save(product);
        log.info("Created product: {} (SKU: {})", saved.getName(), saved.getSku());
        return saved;
    }

    @Transactional
    public Product updateProduct(Long id, Product updates) {
        Product existing = getProductById(id);

        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getCategory() != null) existing.setCategory(updates.getCategory());
        if (updates.getMrp() != null) existing.setMrp(updates.getMrp());
        if (updates.getSalePrice() != null) existing.setSalePrice(updates.getSalePrice());
        if (updates.getUnit() != null) existing.setUnit(updates.getUnit());
        if (updates.getWeightGrams() != null) existing.setWeightGrams(updates.getWeightGrams());
        if (updates.getHsnCode() != null) existing.setHsnCode(updates.getHsnCode());
        if (updates.getGstRate() != null) existing.setGstRate(updates.getGstRate());
        if (updates.getImageUrl() != null) existing.setImageUrl(updates.getImageUrl());
        if (updates.getStockQuantity() != null) existing.setStockQuantity(updates.getStockQuantity());
        if (updates.getLowStockThreshold() != null) existing.setLowStockThreshold(updates.getLowStockThreshold());

        return productRepository.save(existing);
    }

    @Transactional
    public void softDeleteProduct(Long id) {
        Product product = getProductById(id);
        product.setIsActive(false);
        productRepository.save(product);
        log.info("Soft-deleted product: {} (SKU: {})", product.getName(), product.getSku());
    }

    public List<Product> getLowStockProducts() {
        // Find products where stock <= their individual threshold.
        // Use a repository-level query instead of loading the entire products
        // table and filtering in memory.
        return productRepository.findLowStockProducts();
    }
}
