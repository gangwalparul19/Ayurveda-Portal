package com.ayurveda.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for filtering customers.
 * 
 * Supports filtering by various criteria for customer list queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFilterRequest {
    
    /**
     * Filter by customer name (partial match)
     */
    private String name;
    
    /**
     * Filter by phone number (partial match)
     */
    private String phone;
    
    /**
     * Filter by email (partial match)
     */
    private String email;
    
    /**
     * Filter by city
     */
    private String city;
    
    /**
     * Filter by state
     */
    private String state;
    
    /**
     * Filter by customer segment (NEW, REGULAR, VIP, DORMANT)
     */
    private String segment;
}
