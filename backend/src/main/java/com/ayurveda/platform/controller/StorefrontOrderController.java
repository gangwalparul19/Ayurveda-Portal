package com.ayurveda.platform.controller;

import com.ayurveda.platform.security.JwtTokenProvider;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.OrderStatusHistory;
import com.ayurveda.platform.tenant.entity.StorefrontUser;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.StorefrontUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Storefront "My Orders" controller — lets authenticated storefront users
 * view their own order history and order details.
 */
@RestController
@RequestMapping("/storefront/my-orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
@Profile("simple")
public class StorefrontOrderController {

    private final StorefrontUserRepository storefrontUserRepository;
    private final OrderRepository orderRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // -------------------------------------------------------------------------
    // GET /storefront/my-orders
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<Object> getMyOrders(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        StorefrontUser user = resolveUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authentication required"));
        }

        Long customerId = user.getCustomerId();
        if (customerId == null) {
            return ResponseEntity.ok(Map.of("content", List.of(), "totalElements", 0));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId, pageable);

        Page<Map<String, Object>> result = orders.map(this::toOrderSummary);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /storefront/my-orders/{orderId}
    // -------------------------------------------------------------------------

    @GetMapping("/{orderId}")
    public ResponseEntity<Object> getOrderDetail(
            HttpServletRequest request,
            @PathVariable Long orderId) {

        StorefrontUser user = resolveUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authentication required"));
        }

        Long customerId = user.getCustomerId();
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Order not found"));
        }

        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Order not found"));
        }

        return ResponseEntity.ok(toOrderDetail(order));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StorefrontUser resolveUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId == null) {
            return null;
        }
        return storefrontUserRepository.findById(userId).orElse(null);
    }

    private Map<String, Object> toOrderSummary(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId",       order.getId());
        map.put("orderNumber",   order.getOrderNumber());
        map.put("status",        order.getStatus());
        map.put("paymentStatus", order.getPaymentStatus());
        map.put("totalAmount",   order.getTotalAmount());
        map.put("orderDate",     order.getOrderDate());
        map.put("itemCount",     order.getItems() != null ? order.getItems().size() : 0);
        // Brief items summary: first 3 product names
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            List<String> names = order.getItems().stream()
                    .map(OrderItem::getProductNameSnapshot)
                    .limit(3)
                    .collect(Collectors.toList());
            map.put("itemsSummary", names);
        }
        return map;
    }

    private Map<String, Object> toOrderDetail(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId",       order.getId());
        map.put("orderNumber",   order.getOrderNumber());
        map.put("status",        order.getStatus());
        map.put("paymentStatus", order.getPaymentStatus());
        map.put("paymentMode",   order.getPaymentMode());
        map.put("totalAmount",   order.getTotalAmount());
        map.put("subtotal",      order.getSubtotal());
        map.put("shippingCharge",order.getShippingCharge());
        map.put("discountAmount",order.getDiscountAmount());
        map.put("orderDate",     order.getOrderDate());
        map.put("notes",         order.getNotes());

        // Line items
        if (order.getItems() != null) {
            List<Map<String, Object>> items = order.getItems().stream().map(item -> {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("productId",   item.getProduct() != null ? item.getProduct().getId() : null);
                i.put("productName", item.getProductNameSnapshot());
                i.put("sku",         item.getSkuSnapshot());
                i.put("quantity",    item.getQuantity());
                i.put("unitPrice",   item.getUnitPrice());
                i.put("lineTotal",   item.getLineTotal());
                return i;
            }).collect(Collectors.toList());
            map.put("items", items);
        }

        // Status history
        if (order.getStatusHistory() != null) {
            List<Map<String, Object>> history = order.getStatusHistory().stream().map(h -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("fromStatus", h.getFromStatus());
                e.put("toStatus",   h.getToStatus());
                e.put("changedAt",  h.getChangedAt());
                e.put("notes",      h.getNotes());
                return e;
            }).collect(Collectors.toList());
            map.put("statusHistory", history);
        }

        return map;
    }
}
