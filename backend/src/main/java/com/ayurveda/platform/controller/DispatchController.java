package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.response.DispatchLabelDTO;
import com.ayurveda.platform.tenant.entity.DispatchLabel;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.service.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dispatch management controller.
 * Handles dispatch queue management and dispatch label generation (Requirement 12).
 * 
 * Implements REST API endpoints for:
 * - Viewing dispatch queue (PAID/PACKED orders)
 * - Generating single dispatch labels in PDF format (Requirement 12.1-12.7)
 * - Generating bulk dispatch labels for multiple orders (Requirement 12.6)
 * - Marking orders as dispatched
 */
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER', 'SALESPERSON')")
    public ResponseEntity<Page<Order>> getDispatchQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            dispatchService.getDispatchQueue(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "orderDate"))
            )
        );
    }

    @PostMapping("/generate-labels")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER')")
    public ResponseEntity<List<DispatchLabel>> generateLabels(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) request.get("orderIds");
        List<Long> orderIds = ids.stream().map(Integer::longValue).toList();
        String courier = (String) request.getOrDefault("courierPartner", null);
        return ResponseEntity.ok(dispatchService.generateLabels(orderIds, courier));
    }

    @PostMapping("/mark-dispatched")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER')")
    public ResponseEntity<Void> markDispatched(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) request.get("orderIds");
        List<Long> orderIds = ids.stream().map(Integer::longValue).toList();
        dispatchService.markDispatched(orderIds);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Generate single dispatch label PDF for an order.
     * 
     * Implements Requirement 12.1: Generate single dispatch labels for orders
     * Validates that order status is PAID or later (Requirement 12.1)
     * Returns PDF with appropriate content-type headers
     * 
     * Endpoint: GET /api/dispatch/labels/{orderId}
     * 
     * @param orderId The order ID to generate label for
     * @return ResponseEntity containing PDF bytes with appropriate headers
     * @throws ResourceNotFoundException if order not found
     * @throws IllegalStateException if order status is not PAID or later
     */
    @GetMapping("/labels/{orderId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER', 'SALESPERSON')")
    public ResponseEntity<byte[]> generateSingleLabel(@PathVariable Long orderId) {
        // Generate PDF label (service validates order status)
        byte[] pdfBytes = dispatchService.generateSingleLabel(orderId);
        
        // Set appropriate HTTP headers for PDF download
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "dispatch-label-" + orderId + ".pdf");
        headers.setContentLength(pdfBytes.length);
        
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
    
    /**
     * Generate bulk dispatch labels PDF for multiple orders.
     * 
     * Implements Requirement 12.6: Generate bulk dispatch labels for multiple orders
     * Creates a single PDF containing one label page per order
     * Validates that all order statuses are PAID or later
     * Returns PDF with appropriate content-type headers
     * 
     * Endpoint: POST /api/dispatch/labels/bulk
     * Request Body: { "orderIds": [1, 2, 3, ...] }
     * 
     * @param request Map containing list of order IDs under "orderIds" key
     * @return ResponseEntity containing PDF bytes with appropriate headers
     * @throws ResourceNotFoundException if any order not found
     * @throws IllegalArgumentException if orderIds is null or empty
     * @throws IllegalStateException if any order status is not PAID or later
     */
    @PostMapping("/labels/bulk")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER')")
    public ResponseEntity<byte[]> generateBulkLabels(@RequestBody Map<String, Object> request) {
        // Extract order IDs from request
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) request.get("orderIds");
        
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("orderIds cannot be null or empty");
        }
        
        List<Long> orderIds = ids.stream().map(Integer::longValue).toList();
        
        // Generate bulk PDF labels (service validates order statuses)
        byte[] pdfBytes = dispatchService.generateBulkLabels(orderIds);
        
        // Set appropriate HTTP headers for PDF download
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "dispatch-labels-bulk.pdf");
        headers.setContentLength(pdfBytes.length);
        
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
    
    /**
     * Get dispatch label data for an order without generating PDF.
     * Useful for preview or debugging purposes.
     * 
     * Endpoint: GET /api/dispatch/labels/{orderId}/data
     * 
     * @param orderId The order ID to get label data for
     * @return ResponseEntity containing DispatchLabelDTO with all label information
     * @throws ResourceNotFoundException if order not found
     */
    @GetMapping("/labels/{orderId}/data")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'DISPATCHER', 'SALESPERSON')")
    public ResponseEntity<DispatchLabelDTO> getLabelData(@PathVariable Long orderId) {
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(orderId);
        return ResponseEntity.ok(labelData);
    }
}
