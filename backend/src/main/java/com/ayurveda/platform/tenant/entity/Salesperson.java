package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Salesperson entity in the tenant-specific database.
 * Tracks commission rates and links to PlatformUser in the master database.
 */
@Entity
@Table(name = "salespersons", indexes = {
        @Index(name = "idx_salespersons_code", columnList = "employee_code", unique = true),
        @Index(name = "idx_salespersons_status", columnList = "status"),
        @Index(name = "idx_salespersons_user", columnList = "platform_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Salesperson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", unique = true, nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SalespersonStatus status = SalespersonStatus.ACTIVE;

    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Enum for salesperson status
     */
    public enum SalespersonStatus {
        ACTIVE, INACTIVE, ON_LEAVE
    }
}
