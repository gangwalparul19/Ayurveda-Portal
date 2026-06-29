package com.ayurveda.platform.controller;

import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Razorpay payment gateway integration.
 * All endpoints are under /storefront/** and are permitAll() by SecurityConfig.
 */
@RestController
@RequestMapping("/storefront/payment")
@CrossOrigin(origins = "*")
@Profile("simple")
@RequiredArgsConstructor
@Slf4j
public class RazorpayController {

    private final RazorpayService razorpayService;
    private final OrderRepository orderRepository;

    /**
     * POST /storefront/payment/create-order
     * Creates a Razorpay order and returns the order details needed by the frontend.
     * No authentication required.
     */
    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest request) {
        String receipt = "receipt_" + System.currentTimeMillis();
        Map<String, Object> result = razorpayService.createOrder(
                request.getAmount(),
                "INR",
                receipt
        );
        return ResponseEntity.ok(result);
    }

    /**
     * POST /storefront/payment/verify
     * Verifies Razorpay payment signature and updates the order payment status.
     * No authentication required.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody VerifyPaymentRequest request) {
        Map<String, Object> response = new HashMap<>();

        boolean verified = razorpayService.verifyPayment(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (verified) {
            // Update the internal order with payment details
            if (request.getOrderId() != null) {
                orderRepository.findById(request.getOrderId()).ifPresent(order -> {
                    order.setRazorpayOrderId(request.getRazorpayOrderId());
                    order.setRazorpayPaymentId(request.getRazorpayPaymentId());
                    order.setPaymentStatus(Order.PaymentStatus.PAID);
                    order.setPaymentMode(Order.PaymentMode.ONLINE);
                    orderRepository.save(order);
                    log.info("Order {} payment updated to PAID via Razorpay payment {}",
                            order.getOrderNumber(), request.getRazorpayPaymentId());
                });
            }
            response.put("success", true);
            response.put("message", "Payment verified");
        } else {
            log.warn("Payment verification failed for Razorpay order: {}", request.getRazorpayOrderId());
            response.put("success", false);
            response.put("message", "Payment verification failed");
        }

        return ResponseEntity.ok(response);
    }

    // --- Inner request DTOs ---

    public static class CreateOrderRequest {
        private BigDecimal amount;
        private String customerPhone;

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCustomerPhone() { return customerPhone; }
        public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    }

    public static class VerifyPaymentRequest {
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
        private Long orderId;

        public String getRazorpayOrderId() { return razorpayOrderId; }
        public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
        public String getRazorpayPaymentId() { return razorpayPaymentId; }
        public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
        public String getRazorpaySignature() { return razorpaySignature; }
        public void setRazorpaySignature(String razorpaySignature) { this.razorpaySignature = razorpaySignature; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }
}
