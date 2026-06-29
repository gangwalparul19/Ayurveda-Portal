package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.response.InvoiceDTO;
import com.ayurveda.platform.tenant.entity.BillingExport;
import com.ayurveda.platform.tenant.service.BillingExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for billing and Vyapar export operations.
 * Implements Requirement 16: Export orders to Vyapar format
 * 
 * Endpoints:
 * - POST /api/billing/export/vyapar - Export selected orders to Vyapar format
 * - GET /api/billing/export/vyapar/daily - Export daily orders to Vyapar
 * - GET /api/billing/export/vyapar/monthly - Export monthly orders to Vyapar
 * - POST /api/billing/export/gst - Export GST data
 * - GET /api/billing/history - Get export history with audit trail
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingExportService billingExportService;

    /**
     * Export selected orders to Vyapar-compatible CSV format.
     * Endpoint: POST /api/billing/export/vyapar
     * 
     * Request body should contain:
     * - orderIds: List of order IDs to export (can be a comma-separated string or array)
     * 
     * Implements Requirement 16.2: Export orders to Vyapar format
     * 
     * @param request Request body containing order IDs
     * @param authentication Spring Security authentication context
     * @return CSV file as byte array with appropriate headers
     */
    @PostMapping("/export/vyapar")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportSelectedOrdersToVyapar(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        log.info("Exporting selected orders to Vyapar format");
        
        // Extract order IDs from request (support both array and comma-separated string)
        List<Long> orderIds;
        Object orderIdsObj = request.get("orderIds");
        
        if (orderIdsObj instanceof List) {
            orderIds = ((List<?>) orderIdsObj).stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .toList();
        } else if (orderIdsObj instanceof String) {
            String orderIdsStr = (String) orderIdsObj;
            orderIds = List.of(orderIdsStr.split(",")).stream()
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        } else {
            log.error("Invalid orderIds format in request");
            return ResponseEntity.badRequest().build();
        }
        
        if (orderIds.isEmpty()) {
            log.warn("No order IDs provided for export");
            return ResponseEntity.badRequest().build();
        }
        
        log.info("Exporting {} orders to Vyapar format", orderIds.size());
        byte[] csv = billingExportService.exportToVyaparFormat(orderIds);
        
        if (csv.length == 0) {
            log.warn("Export resulted in empty CSV file");
            return ResponseEntity.noContent().build();
        }
        
        String filename = String.format("vyapar_export_%s.csv", 
                LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * Export all orders for a specific date to Vyapar-compatible CSV format.
     * Endpoint: GET /api/billing/export/vyapar/daily
     * 
     * Query parameters:
     * - date: The date to export orders for (format: yyyy-MM-dd)
     * 
     * Implements Requirement 16.3: Support daily order exports
     * 
     * @param date The date to export orders for
     * @param authentication Spring Security authentication context
     * @return CSV file as byte array with appropriate headers
     */
    @GetMapping("/export/vyapar/daily")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportDailyOrdersToVyapar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        log.info("Exporting daily orders to Vyapar format for date: {}", date);
        
        if (date == null) {
            log.error("Date parameter is required");
            return ResponseEntity.badRequest().build();
        }
        
        if (date.isAfter(LocalDate.now())) {
            log.warn("Date cannot be in the future: {}", date);
            return ResponseEntity.badRequest().build();
        }
        
        byte[] csv = billingExportService.exportDailyOrdersToVyapar(date);
        
        if (csv.length == 0) {
            log.info("No orders found for date: {}", date);
            return ResponseEntity.noContent().build();
        }
        
        String filename = String.format("vyapar_daily_%s.csv", 
                date.format(DateTimeFormatter.ISO_DATE));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * Export all orders for a specific month to Vyapar-compatible CSV format.
     * Endpoint: GET /api/billing/export/vyapar/monthly
     * 
     * Query parameters:
     * - month: The year-month to export orders for (format: yyyy-MM)
     * 
     * Implements Requirement 16.3: Support monthly order exports
     * 
     * @param yearMonth The year-month to export orders for
     * @param authentication Spring Security authentication context
     * @return CSV file as byte array with appropriate headers
     */
    @GetMapping("/export/vyapar/monthly")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportMonthlyOrdersToVyapar(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
            Authentication authentication) {
        
        log.info("Exporting monthly orders to Vyapar format for month: {}", yearMonth);
        
        if (yearMonth == null) {
            log.error("Month parameter is required");
            return ResponseEntity.badRequest().build();
        }
        
        if (yearMonth.isAfter(YearMonth.now())) {
            log.warn("Month cannot be in the future: {}", yearMonth);
            return ResponseEntity.badRequest().build();
        }
        
        byte[] csv = billingExportService.exportMonthlyOrdersToVyapar(yearMonth);
        
        if (csv.length == 0) {
            log.info("No orders found for month: {}", yearMonth);
            return ResponseEntity.noContent().build();
        }
        
        String filename = String.format("vyapar_monthly_%s.csv", 
                yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * Export GST data for a date range.
     * Endpoint: POST /api/billing/export/gst
     * 
     * Request body should contain:
     * - dateFrom: Start date (format: yyyy-MM-dd)
     * - dateTo: End date (format: yyyy-MM-dd)
     * 
     * @param request Request body with date range
     * @param authentication Spring Security authentication context
     * @return JSON file as byte array with GST data
     */
    @PostMapping("/export/gst")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportGst(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        
        log.info("Exporting GST data");
        
        LocalDate from = LocalDate.parse(request.get("dateFrom"));
        LocalDate to = LocalDate.parse(request.get("dateTo"));
        
        // Extract user ID from authentication
        Long userId = extractUserId(authentication);
        
        byte[] json = billingExportService.exportGstJson(from, to, userId);
        
        String filename = String.format("gst_export_%s_to_%s.json", 
                from.format(DateTimeFormatter.ISO_DATE),
                to.format(DateTimeFormatter.ISO_DATE));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * Get export history with audit trail.
     * Endpoint: GET /api/billing/history
     * 
     * Implements Requirement 16.4: Track export operations with audit trail
     * 
     * @return List of billing export records
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<List<BillingExport>> getExportHistory() {
        log.info("Fetching billing export history");
        return ResponseEntity.ok(billingExportService.getExportHistory());
    }

    /**
     * Get prepared invoice data for an order.
     * Endpoint: GET /api/billing/invoice/{orderId}/data
     *
     * Returns all data needed to render a GST tax invoice (vendor details,
     * customer details, line items with tax breakdowns, totals and payment
     * status) as an {@link InvoiceDTO}. PDF rendering is handled by a separate
     * endpoint.
     *
     * Implements Requirement 29.1, 29.2: Invoice data preparation
     *
     * @param orderId The order to build invoice data for
     * @param authentication Spring Security authentication context
     * @return InvoiceDTO with complete invoice details
     */
    @GetMapping("/invoice/{orderId}/data")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<InvoiceDTO> getInvoiceData(
            @PathVariable Long orderId,
            Authentication authentication) {

        log.info("Fetching invoice data for order ID: {}", orderId);
        InvoiceDTO invoice = billingExportService.prepareInvoiceData(orderId);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Generate and download a GST tax invoice PDF for an order.
     * Endpoint: GET /api/billing/invoice/{orderId}
     *
     * Renders the prepared {@link InvoiceDTO} into a PDF document containing the
     * vendor header, invoice number/date, customer billing/shipping details, a
     * line item table (HSN, quantity, rate, taxable value, GST), a tax summary
     * (CGST/SGST/IGST), the grand total, payment status and terms. The response is
     * returned as an {@code application/pdf} attachment.
     *
     * Implements Requirement 29.1, 29.2, 29.3, 29.4: Invoice PDF generation
     *
     * @param orderId The order to build the invoice PDF for
     * @param authentication Spring Security authentication context
     * @return PDF file as byte array with attachment headers
     */
    @GetMapping("/invoice/{orderId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> getInvoicePdf(
            @PathVariable Long orderId,
            Authentication authentication) {

        log.info("Generating invoice PDF for order ID: {}", orderId);
        byte[] pdf = billingExportService.generateInvoicePdf(orderId);

        String filename = String.format("invoice_%d.pdf", orderId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Helper method to extract user ID from Spring Security authentication context.
     * 
     * @param authentication Spring Security authentication object
     * @return User ID or null if not available
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            org.springframework.security.core.userdetails.User userDetails = 
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
            try {
                return Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                log.warn("Could not parse user ID from username: {}", userDetails.getUsername());
            }
        }
        return null;
    }
}
