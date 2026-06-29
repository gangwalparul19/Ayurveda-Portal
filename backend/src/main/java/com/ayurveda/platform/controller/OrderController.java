package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.*;
import com.ayurveda.platform.dto.response.DuplicateCheckResult;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.service.OrderService;
import com.ayurveda.platform.util.WhatsAppTextParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Order management controller.
 * Handles CRUD, status transitions, and WhatsApp text parsing.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final WhatsAppTextParser whatsAppParser;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON', 'DISPATCHER')")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long salespersonId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                orderService.getOrdersAsResponse(status, from, to, salespersonId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON', 'DISPATCHER')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderResponseById(id));
    }

    /**
     * Create a manual order with full validation.
     * Endpoint: POST /orders/manual
     * Requirements: 1.1, 1.2, 1.3, 4.1
     */
    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<OrderResponse> createManualOrder(
            @Valid @RequestBody ManualOrderRequest request,
            Authentication authentication) {
        
        // Extract user ID from authentication
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            org.springframework.security.core.userdetails.User userDetails = 
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
            try {
                userId = Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                // Username might not be numeric, that's okay
            }
        }
        
        OrderResponse response = orderService.createManualOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create a WhatsApp order with automatic parsing.
     * Endpoint: POST /orders/whatsapp
     * Requirements: 1.2, 1.3, 3.6
     */
    @PostMapping("/whatsapp")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<OrderResponse> createWhatsAppOrder(
            @Valid @RequestBody WhatsAppOrderRequest request,
            Authentication authentication) {
        
        // Extract user ID from authentication
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            org.springframework.security.core.userdetails.User userDetails = 
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
            try {
                userId = Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                // Username might not be numeric, that's okay
            }
        }
        
        OrderResponse response = orderService.createWhatsAppOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create a storefront order (public endpoint).
     * Endpoint: POST /orders/storefront
     * Requirements: 1.3
     */
    @PostMapping("/storefront")
    public ResponseEntity<OrderResponse> createStorefrontOrder(
            @Valid @RequestBody StorefrontOrderRequest request) {
        
        OrderResponse response = orderService.createStorefrontOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get order by order number.
     * Endpoint: GET /orders/number/{orderNumber}
     * Requirements: 2.1, 2.2
     */
    @GetMapping("/number/{orderNumber}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON', 'DISPATCHER')")
    public ResponseEntity<Order> getOrderByNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByNumber(orderNumber));
    }

    /**
     * Update order details.
     * Endpoint: PUT /orders/{id}
     * Requirements: 1.1, 1.2, 4.1
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        OrderResponse response = orderService.updateOrder(id, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update order status with validation.
     * Endpoint: PUT /orders/{id}/status
     * Requirements: 5.1-5.11, 6.1-6.3
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER')")
    public ResponseEntity<OrderResponse> updateOrderStatusV2(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        OrderResponse response = orderService.updateOrderStatusV2(id, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Check for duplicate orders.
     * Endpoint: POST /orders/duplicate-check
     * Requirements: 11.1-11.5
     */
    @PostMapping("/duplicate-check")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<DuplicateCheckResult> checkDuplicate(
            @Valid @RequestBody DuplicateCheckRequest request) {
        
        DuplicateCheckResult result = orderService.checkDuplicate(
                request.getCustomerPhone(),
                request.getProductIds(),
                request.getOrderDate()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an order with reason.
     * Endpoint: DELETE /orders/{id}
     * Requirements: 27.1-27.4
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest request,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        OrderResponse response = orderService.cancelOrder(id, request.getReason(), userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Return an order with details.
     * Endpoint: POST /orders/{id}/return
     * Requirements: 28.1-28.4
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<OrderResponse> returnOrder(
            @PathVariable Long id,
            @Valid @RequestBody ReturnOrderRequest request,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        OrderResponse response = orderService.returnOrder(id, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk update order status.
     * Endpoint: POST /orders/bulk/status
     * Requirements: 23.1-23.4
     */
    @PostMapping("/bulk/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER')")
    public ResponseEntity<Map<String, Object>> bulkUpdateStatus(
            @Valid @RequestBody BulkStatusUpdateRequest request,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        List<Long> orderIds = request.getOrderIds();
        List<OrderResponse> results = orderService.bulkUpdateStatus(
                orderIds,
                request.getTargetStatus(),
                request.getNotes(),
                userId
        );

        // The service returns results aligned by index with the requested order IDs
        // (null entries indicate an order that failed validation/processing). Build a
        // per-order success/failure breakdown so the client can see partial success.
        List<OrderResponse> succeeded = new java.util.ArrayList<>();
        List<Long> succeededIds = new java.util.ArrayList<>();
        List<Long> failedIds = new java.util.ArrayList<>();

        for (int i = 0; i < orderIds.size(); i++) {
            OrderResponse result = i < results.size() ? results.get(i) : null;
            if (result != null) {
                succeeded.add(result);
                succeededIds.add(orderIds.get(i));
            } else {
                failedIds.add(orderIds.get(i));
            }
        }

        Map<String, Object> response = Map.of(
                "successCount", succeeded.size(),
                "failureCount", failedIds.size(),
                "succeededOrderIds", succeededIds,
                "failedOrderIds", failedIds,
                "results", succeeded
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to extract user ID from authentication.
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            org.springframework.security.core.userdetails.User userDetails = 
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
            try {
                return Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                // Username might not be numeric, return null
            }
        }
        return null;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<Order> createOrder(@RequestBody OrderCreatePayload payload) {
        Order created = orderService.createOrder(payload.getOrder(), payload.getItems());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update order status (deprecated - use PUT /orders/{id}/status instead).
     */
    @Deprecated
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER')")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(body.get("status").toUpperCase());
        Long changedBy = body.containsKey("changedBy") ? Long.parseLong(body.get("changedBy")) : null;
        String notes = body.get("notes");

        Order updated = orderService.updateOrderStatus(id, newStatus, changedBy, notes);
        return ResponseEntity.ok(updated);
    }

    /**
     * Parse WhatsApp text and return structured order preview.
     */
    @PostMapping("/parse-whatsapp")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<WhatsAppTextParser.ParsedWhatsAppOrder> parseWhatsApp(
            @RequestBody Map<String, String> body) {
        String rawText = body.get("text");
        WhatsAppTextParser.ParsedWhatsAppOrder parsed = whatsAppParser.parseWhatsAppMessage(rawText);
        return ResponseEntity.ok(parsed);
    }

    // --- Request payload DTOs ---

    @lombok.Data
    public static class OrderCreatePayload {
        private Order order;
        private List<OrderItem> items;
    }
}
