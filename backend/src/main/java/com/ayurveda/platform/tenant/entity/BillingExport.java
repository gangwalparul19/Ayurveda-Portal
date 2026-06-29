package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks billing export history (Vyapar CSV/Excel, GST JSON, etc.).
 */
@Entity
@Table(name = "billing_exports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_type", nullable = false)
    private ExportType exportType;

    @Column(name = "date_range_start")
    private LocalDate dateRangeStart;

    @Column(name = "date_range_end")
    private LocalDate dateRangeEnd;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "record_count")
    @Builder.Default
    private Integer recordCount = 0;

    @Column(name = "generated_by")
    private Long generatedBy;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    public enum ExportType {
        VYAPAR_CSV, VYAPAR_EXCEL, GST_JSON, CUSTOM
    }
}
