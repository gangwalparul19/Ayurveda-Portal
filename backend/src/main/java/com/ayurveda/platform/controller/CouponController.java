package com.ayurveda.platform.controller;

import com.ayurveda.platform.tenant.entity.Coupon;
import com.ayurveda.platform.tenant.entity.CouponUsage;
import com.ayurveda.platform.tenant.repository.CouponRepository;
import com.ayurveda.platform.tenant.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for coupon/discount code management.
 * 
 * Public endpoint: POST /storefront/coupon/validate (no auth required — under /storefront/**)
 * Admin endpoints: /coupons/** (require TENANT_ADMIN or MANAGER, enforced in SecurityConfig)
 */
@RestController
@CrossOrigin(origins = "*")
@Profile("simple")
@RequiredArgsConstructor
@Slf4j
public class CouponController {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    // ---- Public: validate coupon ----

    /**
     * POST /storefront/coupon/validate
     * Validates a coupon code and calculates the applicable discount.
     * No authentication required.
     */
    @PostMapping("/storefront/coupon/validate")
    public ResponseEntity<Map<String, Object>> validateCoupon(@RequestBody ValidateCouponRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. Find coupon
        Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(request.getCode());
        if (couponOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse("Coupon not found"));
        }
        Coupon coupon = couponOpt.get();

        // 2. Check active
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            return ResponseEntity.badRequest().body(errorResponse("Coupon is not active"));
        }

        // 3. Check validity dates
        LocalDate today = LocalDate.now();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            return ResponseEntity.badRequest().body(errorResponse("Coupon is not yet valid"));
        }
        if (coupon.getValidUntil() != null && today.isAfter(coupon.getValidUntil())) {
            return ResponseEntity.badRequest().body(errorResponse("Coupon has expired"));
        }

        // 4. Check minimum order amount
        if (coupon.getMinOrderAmount() != null
                && request.getOrderAmount() != null
                && request.getOrderAmount().compareTo(coupon.getMinOrderAmount()) < 0) {
            return ResponseEntity.badRequest().body(
                    errorResponse("Minimum order amount is ₹" + coupon.getMinOrderAmount().setScale(0, RoundingMode.FLOOR)));
        }

        // 5. Check total usage limit
        if (coupon.getUsageLimit() != null) {
            int usageCount = coupon.getUsageCount() != null ? coupon.getUsageCount() : 0;
            if (usageCount >= coupon.getUsageLimit()) {
                return ResponseEntity.badRequest().body(errorResponse("Coupon usage limit reached"));
            }
        }

        // 6. Check per-user limit
        if (coupon.getPerUserLimit() != null && request.getCustomerPhone() != null) {
            long userUsageCount = couponUsageRepository.countByCouponIdAndCustomerPhone(
                    coupon.getId(), request.getCustomerPhone());
            if (userUsageCount >= coupon.getPerUserLimit()) {
                return ResponseEntity.badRequest().body(errorResponse("Coupon already used"));
            }
        }

        // 7. Calculate discount
        BigDecimal orderAmount = request.getOrderAmount() != null ? request.getOrderAmount() : BigDecimal.ZERO;
        BigDecimal discountAmount;

        if (coupon.getDiscountType() == Coupon.DiscountType.PERCENT) {
            discountAmount = orderAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            // Cap at maxDiscountAmount if set
            if (coupon.getMaxDiscountAmount() != null
                    && discountAmount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discountAmount = coupon.getMaxDiscountAmount();
            }
        } else {
            // FLAT discount — cannot exceed order amount
            discountAmount = coupon.getDiscountValue().min(orderAmount);
        }

        BigDecimal finalAmount = orderAmount.subtract(discountAmount);

        // 8. Build success response
        response.put("valid", true);
        response.put("couponId", coupon.getId());
        response.put("code", coupon.getCode());
        response.put("description", coupon.getDescription());
        response.put("discountType", coupon.getDiscountType().name());
        response.put("discountValue", coupon.getDiscountValue());
        response.put("discountAmount", discountAmount);
        response.put("finalAmount", finalAmount);

        log.info("Coupon {} validated for phone {}: discount ₹{}", coupon.getCode(),
                request.getCustomerPhone(), discountAmount);
        return ResponseEntity.ok(response);
    }

    // ---- Admin: coupon management ----

    /**
     * GET /coupons — list all coupons (admin only, enforced by SecurityConfig)
     */
    @GetMapping("/coupons")
    public ResponseEntity<List<Coupon>> listCoupons() {
        return ResponseEntity.ok(couponRepository.findAll());
    }

    /**
     * POST /coupons — create a coupon (admin only)
     */
    @PostMapping("/coupons")
    public ResponseEntity<Coupon> createCoupon(@RequestBody Coupon coupon) {
        coupon.setId(null); // prevent client from setting ID
        if (coupon.getUsageCount() == null) coupon.setUsageCount(0);
        if (coupon.getIsActive() == null) coupon.setIsActive(true);
        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created: {}", saved.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * PATCH /coupons/{id}/toggle — toggle isActive (admin only)
     */
    @PatchMapping("/coupons/{id}/toggle")
    public ResponseEntity<Coupon> toggleCoupon(@PathVariable Long id) {
        return couponRepository.findById(id)
                .map(coupon -> {
                    coupon.setIsActive(!Boolean.TRUE.equals(coupon.getIsActive()));
                    Coupon saved = couponRepository.save(coupon);
                    log.info("Coupon {} toggled to active={}", saved.getCode(), saved.getIsActive());
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /coupons/{id} — delete a coupon (admin only)
     */
    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        if (!couponRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        couponRepository.deleteById(id);
        log.info("Coupon {} deleted", id);
        return ResponseEntity.noContent().build();
    }

    // ---- Helpers ----

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("valid", false);
        error.put("message", message);
        return error;
    }

    // ---- Inner request DTO ----

    public static class ValidateCouponRequest {
        private String code;
        private BigDecimal orderAmount;
        private String customerPhone;
        private String category;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public BigDecimal getOrderAmount() { return orderAmount; }
        public void setOrderAmount(BigDecimal orderAmount) { this.orderAmount = orderAmount; }
        public String getCustomerPhone() { return customerPhone; }
        public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}
