package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.CreateProductRequest;
import com.ayurveda.platform.dto.request.ProductFilterRequest;
import com.ayurveda.platform.dto.request.UpdateProductRequest;
import com.ayurveda.platform.dto.response.ProductResponse;
import com.ayurveda.platform.dto.response.StockHistoryResponse;
import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.StockHistory;
import com.ayurveda.platform.tenant.repository.OrderItemRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for product management operations using DTOs.
 * Implements requirements 8.1, 8.2, 8.3, 8.4, 8.5 for product CRUD operations.
 * All operations are scoped to the current tenant via TenantContext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductManagementService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockHistoryRepository stockHistoryRepository;

    /**
     * Create a new product.
     * Requirements: 8.1, 8.2, 8.3
     * - Validates SKU uniqueness (8.2)
     * - Requires SKU, name, sale price, and MRP (8.1)
     * - Allows optional fields for category, description, and weight (8.3)
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.debug("Creating product with SKU: {}", request.getSku());
        
        // Requirement 8.2: Ensure SKU uniqueness
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("Product with SKU already exists: " + request.getSku());
        }

        // Map request to entity
        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .mrp(request.getMrp())
                .salePrice(request.getSalePrice())
                .description(request.getDescription())
                .category(request.getCategory())
                .weightGrams(request.getWeightGrams())
                .unit(request.getUnit() != null ? request.getUnit() : "pcs")
                .hsnCode(request.getHsnCode())
                .gstRate(request.getGstRate() != null ? request.getGstRate() : BigDecimal.ZERO)
                .imageUrl(request.getImageUrl())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10)
                .isActive(true)
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product: {} (SKU: {})", saved.getName(), saved.getSku());
        
        return mapToResponse(saved);
    }

    /**
     * Update an existing product.
     * Requirements: 8.4
     * - Maintains product ID and creation timestamp
     * - Updates only provided fields
     */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        log.debug("Updating product with ID: {}", id);
        
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Update only provided fields (partial update)
        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            existing.setCategory(request.getCategory());
        }
        if (request.getMrp() != null) {
            existing.setMrp(request.getMrp());
        }
        if (request.getSalePrice() != null) {
            existing.setSalePrice(request.getSalePrice());
        }
        if (request.getWeightGrams() != null) {
            existing.setWeightGrams(request.getWeightGrams());
        }
        if (request.getUnit() != null) {
            existing.setUnit(request.getUnit());
        }
        if (request.getHsnCode() != null) {
            existing.setHsnCode(request.getHsnCode());
        }
        if (request.getGstRate() != null) {
            existing.setGstRate(request.getGstRate());
        }
        if (request.getImageUrl() != null) {
            existing.setImageUrl(request.getImageUrl());
        }
        if (request.getLowStockThreshold() != null) {
            existing.setLowStockThreshold(request.getLowStockThreshold());
        }
        if (request.getIsActive() != null) {
            existing.setIsActive(request.getIsActive());
        }

        Product updated = productRepository.save(existing);
        log.info("Updated product: {} (SKU: {})", updated.getName(), updated.getSku());
        
        return mapToResponse(updated);
    }

    /**
     * Delete a product (soft delete).
     * Requirements: 8.5
     * - Prevents deletion if the product appears in any orders
     */
    @Transactional
    public void deleteProduct(Long id) {
        log.debug("Attempting to delete product with ID: {}", id);
        
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Requirement 8.5: Prevent deletion if product appears in any orders
        if (orderItemRepository.existsByProductId(id)) {
            throw new IllegalStateException(
                "Cannot delete product '" + product.getName() + "' (SKU: " + product.getSku() + 
                ") because it appears in one or more orders. Consider deactivating it instead."
            );
        }

        // Soft delete
        product.setIsActive(false);
        productRepository.save(product);
        log.info("Soft-deleted product: {} (SKU: {})", product.getName(), product.getSku());
    }

    /**
     * Get product by ID.
     * Requirements: 8.1
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product with ID: {}", id);
        
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        
        return mapToResponse(product);
    }

    /**
     * Get product by SKU.
     * Requirements: 8.2
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        log.debug("Fetching product with SKU: {}", sku);
        
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        
        return mapToResponse(product);
    }

    /**
     * Get all active products with pagination.
     * Requirements: 8.1
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        log.debug("Fetching all active products with pagination");
        
        return productRepository.findAllByIsActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Get products by category with pagination.
     * Requirements: 8.3
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(String category, Pageable pageable) {
        log.debug("Fetching products by category: {}", category);
        
        return productRepository.findAllByCategoryAndIsActiveTrue(category, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Search products by name or SKU.
     * Requirements: 8.1
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String query) {
        log.debug("Searching products with query: {}", query);
        
        return productRepository.searchByNameOrSku(query).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all product categories.
     * Requirements: 8.3
     */
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        log.debug("Fetching all product categories");
        
        return productRepository.findAllActiveCategories();
    }

    /**
     * Get products with low stock.
     * Requirements: 9.5 (referenced in design)
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        log.debug("Fetching low stock products");
        
        return productRepository.findLowStockProducts().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get products with low stock using custom threshold.
     * Requirements: 9.5, 21.1, 21.2, 21.3, 21.4
     * 
     * @param threshold custom threshold value (overrides product's lowStockThreshold)
     * @return list of products where stockQuantity <= threshold
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts(Integer threshold) {
        log.debug("Fetching low stock products with custom threshold: {}", threshold);
        
        return productRepository.findAllByStockQuantityLessThanEqualAndIsActiveTrue(threshold).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update stock quantity with history tracking.
     * Requirements: 9.1, 9.2, 9.3, 9.4, 9.6, 21.1, 21.2, 21.3, 21.4
     * 
     * @param productId the product ID
     * @param quantity the quantity to change (positive for STOCK_IN/RETURN, negative for STOCK_OUT)
     * @param operation the type of stock operation
     * @param referenceType optional reference type (ORDER, PURCHASE, ADJUSTMENT)
     * @param referenceId optional reference ID (e.g., order ID)
     * @param notes optional notes about the stock change
     * @param performedBy user ID who performed the operation
     * @throws InsufficientStockException if operation would result in negative stock
     */
    @Transactional
    public void updateStock(Long productId, Integer quantity, StockHistory.StockOperation operation,
                           String referenceType, Long referenceId, String notes, Long performedBy) {
        log.debug("Updating stock for product ID: {}, quantity: {}, operation: {}", 
                 productId, quantity, operation);
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        
        Integer quantityBefore = product.getStockQuantity();
        Integer quantityChanged = quantity;
        Integer quantityAfter = quantityBefore + quantity;
        
        // Requirement 9.4: Validate stock never becomes negative
        if (quantityAfter < 0) {
            throw new InsufficientStockException(
                product.getName(), 
                product.getSku(),
                quantityBefore, 
                Math.abs(quantity)
            );
        }
        
        // Update product stock
        product.setStockQuantity(quantityAfter);
        productRepository.save(product);
        
        // Requirement 9.1, 9.6: Create StockHistory record for audit trail
        StockHistory history = StockHistory.builder()
                .product(product)
                .operation(operation)
                .quantityBefore(quantityBefore)
                .quantityChanged(quantityChanged)
                .quantityAfter(quantityAfter)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .notes(notes)
                .performedBy(performedBy)
                .build();
        
        stockHistoryRepository.save(history);
        
        log.info("Updated stock for product '{}' (SKU: {}): {} -> {} (operation: {})", 
                product.getName(), product.getSku(), quantityBefore, quantityAfter, operation);
    }

    /**
     * Simplified updateStock method for basic stock operations.
     * Requirements: 9.1, 9.2, 9.3, 9.4
     * 
     * @param productId the product ID
     * @param quantity the quantity to change
     * @param operation the type of stock operation
     */
    @Transactional
    public void updateStock(Long productId, Integer quantity, StockHistory.StockOperation operation) {
        updateStock(productId, quantity, operation, null, null, null, null);
    }

    /**
     * Get stock history for a product.
     * Requirements: 21.3, 21.4
     * 
     * @param productId the product ID
     * @return list of stock history records
     */
    @Transactional(readOnly = true)
    public List<StockHistory> getStockHistory(Long productId) {
        log.debug("Fetching stock history for product ID: {}", productId);
        
        // Validate product exists
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        
        return stockHistoryRepository.findByProductId(productId);
    }

    /**
     * Get stock history for a product as response DTOs.
     * Requirements: 21.3, 21.4
     * 
     * @param productId the product ID
     * @return list of stock history response DTOs
     */
    @Transactional(readOnly = true)
    public List<StockHistoryResponse> getStockHistoryResponse(Long productId) {
        log.debug("Fetching stock history response for product ID: {}", productId);
        
        // Validate product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        
        return stockHistoryRepository.findByProductId(productId).stream()
                .map(history -> mapToStockHistoryResponse(history, product))
                .collect(Collectors.toList());
    }

    /**
     * Get all products with advanced filtering.
     * Requirements: 8.1, 8.3, 22.1, 22.2
     * 
     * @param filter the filter criteria
     * @return paginated list of products matching the filter
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsWithFilter(ProductFilterRequest filter) {
        log.debug("Fetching products with filter: {}", filter);
        
        // Build pageable with sort
        Pageable pageable = buildPageable(filter);
        
        // Handle different filter scenarios
        if (filter.getLowStockOnly() != null && filter.getLowStockOnly()) {
            return productRepository.findLowStockProducts(pageable)
                    .map(this::mapToResponse);
        }
        
        if (filter.getQuery() != null && !filter.getQuery().isBlank()) {
            // Search by name or SKU with pagination
            return productRepository.searchByNameOrSku(filter.getQuery(), pageable)
                    .map(this::mapToResponse);
        }
        
        if (filter.getCategory() != null && !filter.getCategory().isBlank()) {
            return productRepository.findAllByCategoryAndIsActiveTrue(filter.getCategory(), pageable)
                    .map(this::mapToResponse);
        }
        
        if (filter.getIsActive() != null) {
            if (filter.getIsActive()) {
                return productRepository.findAllByIsActiveTrue(pageable)
                        .map(this::mapToResponse);
            } else {
                return productRepository.findAllByIsActiveFalse(pageable)
                        .map(this::mapToResponse);
            }
        }
        
        // Default: return all active products
        return productRepository.findAllByIsActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Build Pageable from filter request.
     */
    private Pageable buildPageable(ProductFilterRequest filter) {
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getSize() != null ? filter.getSize() : 20;
        
        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(filter.getSortDirection()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC;
        
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    /**
     * Map Product entity to ProductResponse DTO.
     */
    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .mrp(product.getMrp())
                .price(product.getSalePrice())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .weight(product.getWeightGrams() != null ? 
                    product.getWeightGrams().toString() + " g" : null)
                .createdAt(product.getCreatedAt())
                .build();
    }

    /**
     * Map StockHistory entity to StockHistoryResponse DTO.
     */
    private StockHistoryResponse mapToStockHistoryResponse(StockHistory history, Product product) {
        return StockHistoryResponse.builder()
                .id(history.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productSku(product.getSku())
                .operation(history.getOperation())
                .quantityBefore(history.getQuantityBefore())
                .quantityChanged(history.getQuantityChanged())
                .quantityAfter(history.getQuantityAfter())
                .referenceType(history.getReferenceType())
                .referenceId(history.getReferenceId())
                .notes(history.getNotes())
                .performedBy(history.getPerformedBy())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
