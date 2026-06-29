package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.CreateProductRequest;
import com.ayurveda.platform.dto.request.ProductFilterRequest;
import com.ayurveda.platform.dto.request.StockUpdateRequest;
import com.ayurveda.platform.dto.request.UpdateProductRequest;
import com.ayurveda.platform.dto.response.ProductResponse;
import com.ayurveda.platform.dto.response.StockHistoryResponse;
import com.ayurveda.platform.tenant.service.ProductManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product management REST API controller.
 * Operates within the current tenant's database context.
 * Implements Requirement 8 (Product Management) for product CRUD operations and stock management.
 * 
 * Key Endpoints:
 * - POST /products - Create product
 * - GET /products/{id} - Get product by ID
 * - GET /products/sku/{sku} - Get product by SKU
 * - GET /products - List products with filters/pagination
 * - PUT /products/{id} - Update product
 * - DELETE /products/{id} - Delete product
 * - GET /products/search - Search products
 * - GET /products/category/{category} - Get products by category
 * - GET /products/low-stock - Get low stock products
 * - POST /products/{id}/stock - Update stock
 * - GET /products/{id}/stock-history - Get stock history
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductManagementService productManagementService;

    /**
     * Get all products with advanced filtering and pagination.
     * Requirements: 8.1, 8.3, 22.1, 22.2
     * 
     * Supports filtering by:
     * - query: Search by name or SKU
     * - category: Filter by category
     * - isActive: Filter by active status
     * - lowStockOnly: Show only low stock products
     * 
     * @param filter Filter criteria including query, category, active status, pagination
     * @return Paginated list of products matching the filter
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @PageableDefault(size = 20) Pageable pageable) {
        
        // Build filter request from query parameters
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .query(query)
                .category(category)
                .isActive(isActive)
                .lowStockOnly(lowStockOnly)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();
        
        return ResponseEntity.ok(productManagementService.getProductsWithFilter(filter));
    }

    /**
     * Get product by ID.
     * Requirements: 8.1
     * 
     * @param id Product ID
     * @return Product details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productManagementService.getProductById(id));
    }

    /**
     * Get product by SKU.
     * Requirements: 8.2
     * 
     * @param sku Product SKU
     * @return Product details
     */
    @GetMapping("/sku/{sku}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productManagementService.getProductBySku(sku));
    }

    /**
     * Search products by name or SKU.
     * Requirements: 8.1, 22.6
     * 
     * @param q Search query
     * @return List of matching products
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String q) {
        return ResponseEntity.ok(productManagementService.searchProducts(q));
    }

    /**
     * Get all product categories.
     * Requirements: 8.3
     * 
     * @return List of distinct categories
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productManagementService.getAllCategories());
    }

    /**
     * Get products by category with pagination.
     * Requirements: 8.3
     * 
     * @param category Category name
     * @param pageable Pagination parameters
     * @return Paginated list of products in the category
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productManagementService.getProductsByCategory(category, pageable));
    }

    /**
     * Get products with low stock levels.
     * Requirements: 9.5
     * 
     * Returns products where current stock quantity is at or below the configured low stock threshold.
     * 
     * @return List of low stock products
     */
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(productManagementService.getLowStockProducts());
    }

    /**
     * Create a new product.
     * Requirements: 8.1, 8.2, 8.3
     * 
     * Validates:
     * - SKU uniqueness (8.2)
     * - Required fields: SKU, name, sale price, MRP (8.1)
     * 
     * @param request Product creation request
     * @return Created product details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productManagementService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update an existing product.
     * Requirements: 8.4
     * 
     * Supports partial updates - only provided fields are updated.
     * Product ID and creation timestamp are maintained.
     * 
     * @param id Product ID
     * @param request Product update request
     * @return Updated product details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productManagementService.updateProduct(id, request));
    }

    /**
     * Delete a product (soft delete).
     * Requirements: 8.5
     * 
     * Prevents deletion if the product appears in any orders.
     * Performs soft delete by setting isActive to false.
     * 
     * @param id Product ID
     * @return No content on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productManagementService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Update product stock quantity.
     * Requirements: 9.1, 9.2, 9.3, 9.4, 9.6
     * 
     * Records stock movement with audit trail in StockHistory.
     * Validates that stock quantity never becomes negative (9.4).
     * 
     * Supported operations:
     * - STOCK_IN: Add stock (positive quantity)
     * - STOCK_OUT: Remove stock (negative quantity)
     * - ADJUSTMENT: Manual adjustment (positive or negative)
     * - RETURN: Return from customer (positive quantity)
     * 
     * @param id Product ID
     * @param request Stock update request with quantity, operation, and optional notes
     * @param authentication Current user authentication
     * @return No content on success
     */
    @PostMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<Void> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody StockUpdateRequest request,
            Authentication authentication) {
        
        // Extract user ID from authentication
        Long userId = extractUserId(authentication);
        
        productManagementService.updateStock(
                id,
                request.getQuantity(),
                request.getOperation(),
                request.getReferenceType(),
                request.getReferenceId(),
                request.getNotes(),
                userId
        );
        
        return ResponseEntity.noContent().build();
    }

    /**
     * Get stock history for a product.
     * Requirements: 21.3, 21.4
     * 
     * Returns complete audit trail of all stock movements for a product.
     * Includes quantity before, changed, after, operation type, and user who performed the operation.
     * 
     * @param id Product ID
     * @return List of stock history records ordered by timestamp
     */
    @GetMapping("/{id}/stock-history")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<List<StockHistoryResponse>> getStockHistory(@PathVariable Long id) {
        return ResponseEntity.ok(productManagementService.getStockHistoryResponse(id));
    }

    /**
     * Extract user ID from authentication context.
     * 
     * @param authentication Current authentication
     * @return User ID or null if not available
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            org.springframework.security.core.userdetails.User userDetails = 
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
            try {
                return Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
