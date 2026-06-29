package com.ayurveda.platform.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for placing orders from storefront.
 * Contains customer details and order items.
 *
 * Implements Requirements:
 * - 31.1: Validate phone numbers in Indian format (10 digits with optional +91/0 prefix)
 * - 31.2: Validate email addresses in RFC 5322 format
 * - 31.3: Validate monetary amounts are non-negative
 */
@Data
public class StorefrontOrderRequest {

    // Customer Information
    @NotBlank(message = "Customer name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String customerName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(\\+91|91|0)?[6-9][0-9]{9}$",
             message = "Invalid phone number format. Must be 10 digits starting with 6-9")
    private String customerPhone;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String customerEmail;

    // Delivery Address
    @NotBlank(message = "Delivery address is required")
    @Size(max = 500, message = "Delivery address must not exceed 500 characters")
    private String deliveryAddress;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be exactly 6 digits")
    private String pincode;

    // Order Items
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    // Additional Information
    private String notes;
    
    private String paymentMethod; // COD, ONLINE, etc.

    private String couponCode;

    private java.math.BigDecimal couponDiscount;

    /**
     * Order item sub-DTO
     */
    @Data
    public static class OrderItemRequest {

        @NotNull(message = "Product ID is required")
        private Long productId;

        private String productName;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @Positive(message = "Price must be greater than 0")
        private Double price;
    }
}
