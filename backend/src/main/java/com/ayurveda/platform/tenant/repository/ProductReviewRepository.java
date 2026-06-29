package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Page<ProductReview> findByProductIdAndIsApprovedTrue(Long productId, Pageable pageable);

    long countByProductIdAndIsApprovedTrue(Long productId);

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId AND r.isApproved = true")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    Page<ProductReview> findByIsApprovedFalse(Pageable pageable);
}
