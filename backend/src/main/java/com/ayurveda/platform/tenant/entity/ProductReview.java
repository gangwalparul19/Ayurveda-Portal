package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Product review entity — shopper-submitted reviews for products.
 * Reviews must be approved by an admin before they appear publicly.
 */
@Entity
@Table(name = "product_reviews", indexes = {
        @Index(name = "idx_reviews_product", columnList = "product_id"),
        @Index(name = "idx_reviews_approved", columnList = "is_approved")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Optional FK to customers.id — present when reviewer is a registered customer. */
    @Column(name = "customer_id")
    private Long customerId;

    /** Optional FK to storefront_users.id — present when reviewer is a storefront user. */
    @Column(name = "storefront_user_id")
    private Long storefrontUserId;

    @Column(name = "reviewer_name", nullable = false, length = 200)
    private String reviewerName;

    /** Star rating 1–5. */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "is_verified_purchase")
    @Builder.Default
    private Boolean isVerifiedPurchase = false;

    @Column(name = "is_approved")
    @Builder.Default
    private Boolean isApproved = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
