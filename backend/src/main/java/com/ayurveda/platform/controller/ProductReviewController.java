package com.ayurveda.platform.controller;

import com.ayurveda.platform.security.JwtTokenProvider;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.ProductReview;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.repository.ProductReviewRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public product review endpoints (storefront) and admin review management.
 */
@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
@Profile("simple")
public class ProductReviewController {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // =========================================================================
    // PUBLIC STOREFRONT endpoints — /storefront/reviews/**
    // =========================================================================

    /**
     * GET /storefront/reviews/{productId}
     * Returns approved reviews with pagination and aggregate stats.
     */
    @GetMapping("/storefront/reviews/{productId}")
    public ResponseEntity<Object> getReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ProductReview> reviewPage = reviewRepository.findByProductIdAndIsApprovedTrue(
                productId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        long totalCount   = reviewRepository.countByProductIdAndIsApprovedTrue(productId);
        Double avgRating  = reviewRepository.findAverageRatingByProductId(productId);

        var reviews = reviewPage.getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",                  r.getId());
            m.put("reviewerName",        r.getReviewerName());
            m.put("rating",              r.getRating());
            m.put("title",               r.getTitle());
            m.put("reviewText",          r.getReviewText());
            m.put("isVerifiedPurchase",  r.getIsVerifiedPurchase());
            m.put("createdAt",           r.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reviews",       reviews);
        response.put("totalCount",    totalCount);
        response.put("averageRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null);
        response.put("totalPages",    reviewPage.getTotalPages());
        response.put("currentPage",   page);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /storefront/reviews/{productId}
     * Submit a new review. Requires admin approval before it appears publicly.
     * If a valid JWT is present the review is linked to that storefront user.
     */
    @PostMapping("/storefront/reviews/{productId}")
    public ResponseEntity<Object> submitReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Product not found"));
        }

        String reviewerName = (String) body.get("reviewerName");
        Object ratingObj    = body.get("rating");
        if (!StringUtils.hasText(reviewerName) || ratingObj == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "reviewerName and rating are required"));
        }

        int rating;
        try {
            rating = Integer.parseInt(ratingObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "rating must be an integer 1-5"));
        }
        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "rating must be between 1 and 5"));
        }

        // Optionally link to storefront user from JWT
        Long storefrontUserId = extractStorefrontUserId(request);

        ProductReview review = ProductReview.builder()
                .product(product)
                .reviewerName(reviewerName)
                .rating(rating)
                .title((String) body.get("title"))
                .reviewText((String) body.get("reviewText"))
                .storefrontUserId(storefrontUserId)
                .isVerifiedPurchase(false)
                .isApproved(false)
                .build();

        reviewRepository.save(review);
        log.info("Review submitted for product {} by {}", productId, reviewerName);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "message", "Review submitted and pending approval"));
    }

    // =========================================================================
    // ADMIN endpoints — /reviews/**  (requires TENANT_ADMIN or MANAGER)
    // =========================================================================

    /**
     * GET /reviews/pending
     * List unapproved reviews.
     */
    @GetMapping("/reviews/pending")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<Object> getPendingReviews(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ProductReview> pending = reviewRepository.findByIsApprovedFalse(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(pending.map(this::toAdminReviewMap));
    }

    /**
     * PATCH /reviews/{reviewId}/approve
     * Approve a review so it appears publicly.
     */
    @PatchMapping("/reviews/{reviewId}/approve")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<Object> approveReview(@PathVariable Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Review not found"));
        }
        review.setIsApproved(true);
        reviewRepository.save(review);
        log.info("Review {} approved", reviewId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Review approved"));
    }

    /**
     * DELETE /reviews/{reviewId}
     * Delete a review (any approval state).
     */
    @DeleteMapping("/reviews/{reviewId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ResponseEntity<Object> deleteReview(@PathVariable Long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Review not found"));
        }
        reviewRepository.deleteById(reviewId);
        log.info("Review {} deleted", reviewId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long extractStorefrontUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }

    private Map<String, Object> toAdminReviewMap(ProductReview r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                  r.getId());
        m.put("productId",           r.getProduct() != null ? r.getProduct().getId() : null);
        m.put("productName",         r.getProduct() != null ? r.getProduct().getName() : null);
        m.put("reviewerName",        r.getReviewerName());
        m.put("rating",              r.getRating());
        m.put("title",               r.getTitle());
        m.put("reviewText",          r.getReviewText());
        m.put("isVerifiedPurchase",  r.getIsVerifiedPurchase());
        m.put("isApproved",          r.getIsApproved());
        m.put("storefrontUserId",    r.getStorefrontUserId());
        m.put("createdAt",           r.getCreatedAt());
        return m;
    }
}
