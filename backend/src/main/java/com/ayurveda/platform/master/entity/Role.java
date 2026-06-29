package com.ayurveda.platform.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Role entity for Role-Based Access Control (RBAC).
 * Defines user roles and their associated permissions.
 * 
 * Requirements: 18.1, 19.1, 19.2, 19.3, 19.4, 19.5
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", unique = true, nullable = false, length = 50)
    private String roleName; // ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Predefined role names as constants
     */
    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String SALESPERSON = "SALESPERSON";
    public static final String DISPATCHER = "DISPATCHER";
    public static final String ACCOUNTANT = "ACCOUNTANT";

    /**
     * Permission constants for different operations
     */
    public static class Permissions {
        // User Management
        public static final String USER_CREATE = "user:create";
        public static final String USER_READ = "user:read";
        public static final String USER_UPDATE = "user:update";
        public static final String USER_DELETE = "user:delete";

        // Order Management
        public static final String ORDER_CREATE = "order:create";
        public static final String ORDER_READ = "order:read";
        public static final String ORDER_UPDATE = "order:update";
        public static final String ORDER_DELETE = "order:delete";
        public static final String ORDER_STATUS_UPDATE = "order:status:update";
        public static final String ORDER_CANCEL = "order:cancel";

        // Product Management
        public static final String PRODUCT_CREATE = "product:create";
        public static final String PRODUCT_READ = "product:read";
        public static final String PRODUCT_UPDATE = "product:update";
        public static final String PRODUCT_DELETE = "product:delete";
        public static final String STOCK_UPDATE = "stock:update";

        // Customer Management
        public static final String CUSTOMER_CREATE = "customer:create";
        public static final String CUSTOMER_READ = "customer:read";
        public static final String CUSTOMER_UPDATE = "customer:update";
        public static final String CUSTOMER_DELETE = "customer:delete";

        // Payment Management
        public static final String PAYMENT_RECORD = "payment:record";
        public static final String PAYMENT_READ = "payment:read";

        // Dispatch Operations
        public static final String DISPATCH_LABEL_GENERATE = "dispatch:label:generate";
        public static final String DISPATCH_VIEW = "dispatch:view";

        // Report Access
        public static final String REPORT_VIEW = "report:view";
        public static final String REPORT_EXPORT = "report:export";

        // Configuration
        public static final String CONFIG_READ = "config:read";
        public static final String CONFIG_UPDATE = "config:update";

        // Salesperson Management
        public static final String SALESPERSON_MANAGE = "salesperson:manage";
    }

    /**
     * Helper method to add a permission to the role
     */
    public void addPermission(String permission) {
        if (this.permissions == null) {
            this.permissions = new HashSet<>();
        }
        this.permissions.add(permission);
    }

    /**
     * Helper method to remove a permission from the role
     */
    public void removePermission(String permission) {
        if (this.permissions != null) {
            this.permissions.remove(permission);
        }
    }

    /**
     * Check if role has a specific permission
     */
    public boolean hasPermission(String permission) {
        return this.permissions != null && this.permissions.contains(permission);
    }
}
