package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.StorefrontOrderRequest;
import com.ayurveda.platform.dto.response.ProductResponse;
import com.ayurveda.platform.dto.response.StorefrontConfigResponse;
import com.ayurveda.platform.master.entity.TenantUiConfig;
import com.ayurveda.platform.master.repository.TenantUiConfigRepository;
import com.ayurveda.platform.security.TenantContext;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public storefront controller for customer-facing e-commerce features.
 * No authentication required for browsing, but orders need customer details.
 * 
 * Endpoints:
 * - GET /storefront/{tenantKey}/config - Get tenant branding/config
 * - GET /storefront/{tenantKey}/products - Browse products
 * - GET /storefront/{tenantKey}/products/{id} - Product details
 * - GET /storefront/{tenantKey}/products/search - Search products
 * - GET /storefront/{tenantKey}/categories - Get categories
 * - POST /storefront/{tenantKey}/orders - Place order
 */
@RestController
@RequestMapping("/storefront")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Profile("dev")  // Only active in dev mode (multi-tenant), disabled in simple mode
public class StorefrontController {

    private final TenantUiConfigRepository uiConfigRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;

    /**
     * Get storefront configuration (branding, colors, logo, etc.)
     */
    @GetMapping("/{tenantKey}/config")
    public ResponseEntity<StorefrontConfigResponse> getStorefrontConfig(@PathVariable String tenantKey) {
        // Set tenant context for data source routing
        TenantContext.setTenantKey(tenantKey);
        
        try {
            TenantUiConfig config = uiConfigRepository.findByTenantTenantKey(tenantKey)
                    .orElseThrow(() -> new RuntimeException("Tenant not found or storefront not enabled"));

            if (!Boolean.TRUE.equals(config.getStorefrontEnabled())) {
                return ResponseEntity.status(403).build();
            }

            StorefrontConfigResponse response = StorefrontConfigResponse.builder()
                    .companyName(config.getTenant().getCompanyName())
                    .primaryColor(config.getPrimaryColor())
                    .secondaryColor(config.getSecondaryColor())
                    .accentColor(config.getAccentColor())
                    .logoUrl(config.getLogoUrl())
                    .faviconUrl(config.getFaviconUrl())
                    .fontFamily(config.getFontFamily())
                    .customCss(config.getCustomCss())
                    .storefrontConfig(config.getStorefrontConfig())
                    .build();

            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Get all products (paginated)
     */
    @GetMapping("/{tenantKey}/products")
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @PathVariable String tenantKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String category) {
        
        TenantContext.setTenantKey(tenantKey);
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("asc") 
                    ? Sort.by(sortBy).ascending() 
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            // Public storefront exposes only active products
            Page<Product> products;
            if (category != null && !category.isEmpty()) {
                products = productRepository.findAllByCategoryAndIsActiveTrue(category, pageable);
            } else {
                products = productRepository.findAllByIsActiveTrue(pageable);
            }

            Page<ProductResponse> response = products.map(this::mapToResponse);
            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Get single product by ID
     */
    @GetMapping("/{tenantKey}/products/{productId}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable String tenantKey,
            @PathVariable Long productId) {
        
        TenantContext.setTenantKey(tenantKey);
        
        try {
            Product product = productRepository.findById(productId)
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            return ResponseEntity.ok(mapToResponse(product));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Search products by name or description
     */
    @GetMapping("/{tenantKey}/products/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @PathVariable String tenantKey,
            @RequestParam String query) {
        
        TenantContext.setTenantKey(tenantKey);
        
        try {
            List<Product> products = productRepository.searchProducts(query);
            List<ProductResponse> response = products.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Get all unique product categories
     */
    @GetMapping("/{tenantKey}/categories")
    public ResponseEntity<List<String>> getCategories(@PathVariable String tenantKey) {
        TenantContext.setTenantKey(tenantKey);
        
        try {
            List<String> categories = productRepository.findDistinctCategories();
            return ResponseEntity.ok(categories);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Place an order (creates customer if new)
     */
    @PostMapping("/{tenantKey}/orders")
    public ResponseEntity<Order> placeOrder(
            @PathVariable String tenantKey,
            @Valid @RequestBody StorefrontOrderRequest request) {
        
        TenantContext.setTenantKey(tenantKey);
        
        try {
            // Find or create customer
            Customer customer = customerRepository
                    .findByPhone(request.getCustomerPhone())
                    .orElseGet(() -> {
                        Customer newCustomer = new Customer();
                        newCustomer.setName(request.getCustomerName());
                        newCustomer.setPhone(request.getCustomerPhone());
                        newCustomer.setEmail(request.getCustomerEmail());
                        newCustomer.setAddressLine1(request.getDeliveryAddress());
                        newCustomer.setCity(request.getCity());
                        newCustomer.setState(request.getState());
                        newCustomer.setPincode(request.getPincode());
                        return customerRepository.save(newCustomer);
                    });

            // Create order
            Order order = orderService.createStorefrontOrder(customer, request);

            log.info("Storefront order created: {} for customer: {}", 
                    order.getId(), customer.getName());

            return ResponseEntity.ok(order);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Get featured/trending products
     */
    @GetMapping("/{tenantKey}/products/featured")
    public ResponseEntity<List<ProductResponse>> getFeaturedProducts(
            @PathVariable String tenantKey,
            @RequestParam(defaultValue = "12") int limit) {
        
        TenantContext.setTenantKey(tenantKey);
        
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
            Page<Product> products = productRepository.findAllByIsActiveTrue(pageable);
            
            List<ProductResponse> response = products.getContent().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    // Helper method to map Product to ProductResponse
    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getSalePrice())
                .mrp(product.getMrp())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .sku(product.getSku())
                .weight(product.getWeightGrams() != null ? product.getWeightGrams().toString() + "g" : null)
                .dimensions(null) // Not available in Product entity
                .createdAt(product.getCreatedAt())
                .build();
    }
}
