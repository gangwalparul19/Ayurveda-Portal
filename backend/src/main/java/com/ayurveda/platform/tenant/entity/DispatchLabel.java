package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dispatch label entity for shipping order packages.
 */
@Entity
@Table(name = "dispatch_labels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "batch_id", length = 50)
    private String batchId;

    @Column(name = "courier_partner", length = 100)
    private String courierPartner;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "label_pdf_path", length = 500)
    private String labelPdfPath;

    @Column(name = "weight_grams", precision = 10, scale = 2)
    private BigDecimal weightGrams;

    @Column(name = "dimensions", length = 50)
    private String dimensions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private LabelStatus status = LabelStatus.GENERATED;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    public enum LabelStatus {
        GENERATED, PRINTED, SHIPPED, DELIVERED
    }
}
