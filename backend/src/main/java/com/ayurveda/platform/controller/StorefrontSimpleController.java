package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.StorefrontOrderRequest;
import com.ayurveda.platform.dto.response.ProductResponse;
import com.ayurveda.platform.master.service.EmailNotificationService;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.service.OrderService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SIMPLIFIED Storefront Controller for Shifa Only
 * No multi-tenant complexity - direct database access
 * 
 * Endpoints:
 * - GET /storefront/products - Browse products
 * - GET /storefront/products/{id} - Product details
 * - GET /storefront/products/search - Search products
 * - GET /storefront/categories - Get categories
 * - GET /storefront/products/featured - Featured products
 * - POST /storefront/orders - Place order
 */
@RestController
@RequestMapping("/storefront")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Profile("simple")  // Only active when profile is 'simple'
public class StorefrontSimpleController {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final EmailNotificationService emailService;

    /**
     * Get all products (paginated)
     */
    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String category) {
        
        Sort sort = sortDir.equalsIgnoreCase("asc") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products;
        if (category != null && !category.isEmpty()) {
            products = productRepository.findAllByCategoryAndIsActiveTrue(category, pageable);
        } else {
            products = productRepository.findAllByIsActiveTrue(pageable);
        }

        Page<ProductResponse> response = products.map(this::mapToResponse);
        log.info("Fetched {} products (page {}/{})", response.getNumberOfElements(), page, response.getTotalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Get single product by ID
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new RuntimeException("Product not found"));

        log.info("Fetched product: {}", product.getName());
        return ResponseEntity.ok(mapToResponse(product));
    }

    /**
     * Search products by name or description
     */
    @GetMapping("/products/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String query) {
        List<Product> products = productRepository.searchProducts(query);
        List<ProductResponse> response = products.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.info("Search '{}' returned {} products", query, response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all unique product categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        List<String> categories = productRepository.findDistinctCategories();
        log.info("Fetched {} categories", categories.size());
        return ResponseEntity.ok(categories);
    }

    /**
     * Get featured/trending products
     */
    @GetMapping("/products/featured")
    public ResponseEntity<List<ProductResponse>> getFeaturedProducts(
            @RequestParam(defaultValue = "12") int limit) {
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        Page<Product> products = productRepository.findAllByIsActiveTrue(pageable);
        
        List<ProductResponse> response = products.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.info("Fetched {} featured products", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Place an order (creates customer if new)
     */
    @PostMapping("/orders")
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody StorefrontOrderRequest request) {
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

        // Send order confirmation email (fire-and-forget; exceptions are swallowed inside)
        emailService.sendOrderConfirmation(order, customer);

        log.info("Order created: {} for customer: {}", order.getId(), customer.getName());
        return ResponseEntity.ok(order);
    }

    /**
     * Get storefront config (static for Shifa)
     */
    @GetMapping("/config")
    public ResponseEntity<Object> getConfig() {
        return ResponseEntity.ok(new Object() {
            public final String companyName = "Shifa Ayurveda";
            public final String primaryColor = "#2E7D32";
            public final String secondaryColor = "#FF6F00";
            public final String accentColor = "#1976D2";
            public final String logoUrl = "/images/shifa/logo.png";
            public final boolean storefrontEnabled = true;
        });
    }

    /**
     * POST /storefront/products/{productId}/share
     * Increment WhatsApp share count. No auth required.
     */
    @PostMapping("/products/{productId}/share")
    @Transactional
    public ResponseEntity<Map<String, Object>> trackShare(@PathVariable Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Product not found"));
        }
        int newCount = (product.getWhatsappShareCount() != null ? product.getWhatsappShareCount() : 0) + 1;
        product.setWhatsappShareCount(newCount);
        productRepository.save(product);
        return ResponseEntity.ok(Map.of("shareCount", newCount));
    }

    /**
     * POST /storefront/products/{productId}/view
     * Increment product view count. No auth required.
     */
    @PostMapping("/products/{productId}/view")
    @Transactional
    public ResponseEntity<Void> trackView(@PathVariable Long productId) {
        productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .ifPresent(p -> {
                    p.setViewCount((p.getViewCount() != null ? p.getViewCount() : 0) + 1);
                    productRepository.save(p);
                });
        return ResponseEntity.ok().build();
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
                .dimensions(null)
                .createdAt(product.getCreatedAt())
                .build();
    }
}
