package com.ayurveda.platform.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Customer entity in the tenant-specific database.
 * Phone number is the primary identifier for WhatsApp orders.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_phone", columnList = "phone", unique = true),
        @Index(name = "idx_customers_name", columnList = "name"),
        @Index(name = "idx_customers_city", columnList = "city")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // Uniqueness enforced by the named unique index idx_customers_phone (@Table indexes)
    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address_line_1", length = 500)
    private String addressLine1;

    @Column(name = "address_line_2", length = 500)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "gstin", length = 20)
    private String gstin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
