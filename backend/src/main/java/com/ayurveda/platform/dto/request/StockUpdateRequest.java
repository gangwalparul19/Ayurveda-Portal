package com.ayurveda.platform.dto.request;

import com.ayurveda.platform.tenant.entity.StockHistory;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for stock update operations.
 * Used to record stock movements with audit information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateRequest {

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    @NotNull(message = "Operation type is required")
    private StockHistory.StockOperation operation;

    private String referenceType;    // ORDER, PURCHASE, ADJUSTMENT
    private Long referenceId;        // Reference to order or purchase ID
    private String notes;            // Optional notes about the stock change
}
