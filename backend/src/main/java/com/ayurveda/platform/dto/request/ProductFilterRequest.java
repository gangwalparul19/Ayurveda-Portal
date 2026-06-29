package com.ayurveda.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for filtering and searching products.
 * Used for pagination and advanced search queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterRequest {

    private String query;           // Search by name or SKU
    private String category;        // Filter by category
    private Boolean isActive;       // Filter by active status
    private Boolean lowStockOnly;   // Show only low stock products
    
    // Pagination
    private Integer page;
    private Integer size;
    private String sortBy;          // Field name to sort by (e.g., "name", "createdAt")
    private String sortDirection;   // "asc" or "desc"
}
