package com.ayurveda.platform.controller;

import com.ayurveda.platform.tenant.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for comprehensive reporting and analytics.
 * Supports sales reports, collection reports, product analysis, geographic analysis, and export functionality.
 * Requirements: 13, 14, 15
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'ACCOUNTANT')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(reportService.getDashboardStats());
    }

    // ==================== SALES REPORTS ====================

    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.getDailyReport(date));
    }

    /**
     * Daily sales report endpoint - Requirements 13.1, 13.2, 13.3, 13.4
     */
    @GetMapping("/daily-sales")
    public ResponseEntity<Map<String, Object>> getDailySalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.getDailySalesReport(date));
    }

    @GetMapping("/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyReport(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(reportService.getMonthlyReport(month, year));
    }

    /**
     * Monthly sales report endpoint - Requirements 13.1, 13.2, 13.3, 13.4
     */
    @GetMapping("/monthly-sales")
    public ResponseEntity<Map<String, Object>> getMonthlySalesReport(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(reportService.getMonthlySalesReport(month, year));
    }

    /**
     * Salesperson performance report.
     */
    @GetMapping("/salesperson-performance")
    public ResponseEntity<List<Map<String, Object>>> getSalespersonPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getSalespersonPerformance(startDate, endDate));
    }

    /**
     * Salesperson sales report for a specific salesperson.
     * Requirements: 13.5, 20.5
     */
    @GetMapping("/salesperson-sales")
    public ResponseEntity<Map<String, Object>> getSalespersonSalesReport(
            @RequestParam Long salespersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getSalespersonSalesReport(salespersonId, startDate, endDate));
    }

    // ==================== COLLECTION REPORTS ====================
    // TODO: Uncomment these endpoints once collection report methods are implemented in ReportService

    /*
     * Daily collection report showing payments received on a specific date.
     * Requirements: 13
     *
     * TODO: Implement ReportService.getDailyCollectionReport(LocalDate date)
     */
    /*
    @GetMapping("/collection/daily")
    public ResponseEntity<Map<String, Object>> getDailyCollectionReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.getDailyCollectionReport(date));
    }
    */

    /*
     * Monthly collection report showing payments received in a specific month.
     * Requirements: 13
     *
     * TODO: Implement ReportService.getMonthlyCollectionReport(int month, int year)
     */
    /*
    @GetMapping("/collection/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyCollectionReport(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(reportService.getMonthlyCollectionReport(month, year));
    }
    */

    /*
     * Salesperson collection report showing payments collected by a specific salesperson.
     * Requirements: 13.5
     *
     * TODO: Implement ReportService.getSalespersonCollectionReport(Long, LocalDate, LocalDate)
     */
    /*
    @GetMapping("/collection/salesperson")
    public ResponseEntity<Map<String, Object>> getSalespersonCollectionReport(
            @RequestParam Long salespersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getSalespersonCollectionReport(salespersonId, startDate, endDate));
    }
    */

    // ==================== PRODUCT REPORTS ====================

    // ==================== PRODUCT REPORTS ====================

    /**
     * Top products report by revenue and quantity.
     */
    @GetMapping("/top-products")
    public ResponseEntity<Map<String, Object>> getTopProductsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.getTopProductsReport(startDate, endDate, limit));
    }

    /**
     * Customer analytics report.
     */
    @GetMapping("/customer-analytics")
    public ResponseEntity<Map<String, Object>> getCustomerAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getCustomerAnalytics(startDate, endDate));
    }

    /**
     * Product-wise sales report aggregating by product for a date range.
     * Includes product name, total quantity sold, and total sales amount.
     * Requirements: 14.1, 14.2
     */
    @GetMapping("/products")
    public ResponseEntity<List<Map<String, Object>>> getProductWiseSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getProductWiseSalesReport(startDate, endDate));
    }

    /**
     * Product-wise sales report (alias endpoint).
     * Requirements: 14.1, 14.2
     */
    @GetMapping("/product-wise-sales")
    public ResponseEntity<List<Map<String, Object>>> getProductWiseSalesReportAlias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getProductWiseSalesReport(startDate, endDate));
    }

    /**
     * Get top-selling products by quantity sold for a date range.
     * Requirements: 14.5
     */
    @GetMapping("/top-selling-products")
    public ResponseEntity<List<Map<String, Object>>> getTopSellingProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(reportService.getTopSellingProducts(startDate, endDate, limit));
    }

    // ==================== GEOGRAPHIC REPORTS ====================

    /**
     * State-wise sales report aggregating by customer state.
     * Requirements: 14.3, 14.4
     */
    @GetMapping("/geography/state")
    public ResponseEntity<Map<String, Object>> getStateWiseSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getStateWiseSalesReport(startDate, endDate));
    }

    /**
     * State-wise sales report (alias endpoint).
     * Requirements: 14.3, 14.4
     */
    @GetMapping("/state-wise-sales")
    public ResponseEntity<Map<String, Object>> getStateWiseSalesReportAlias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getStateWiseSalesReport(startDate, endDate));
    }

    /**
     * City-wise sales report with optional state filter.
     * Requirements: 14.3, 14.4
     */
    @GetMapping("/city-wise-sales")
    public ResponseEntity<Map<String, Object>> getCityWiseSalesReport(
            @RequestParam(required = false) String state,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getCityWiseSalesReport(state, startDate, endDate));
    }

    // ==================== EXPORT - CSV ====================

    // ==================== EXPORT - CSV ====================

    /**
     * Export daily sales report to CSV format.
     * Requirements: 15.1, 15.4
     */
    @GetMapping("/daily-sales/export/csv")
    public ResponseEntity<byte[]> exportDailySalesReportToCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> reportData = reportService.getDailySalesReport(date);
        byte[] csvData = reportService.exportReportToCSV("DAILY_SALES", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "daily-sales-" + date + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Export monthly sales report to CSV format.
     * Requirements: 15.1, 15.4
     */
    @GetMapping("/monthly-sales/export/csv")
    public ResponseEntity<byte[]> exportMonthlySalesReportToCSV(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> reportData = reportService.getMonthlySalesReport(month, year);
        byte[] csvData = reportService.exportReportToCSV("MONTHLY_SALES", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "monthly-sales-" + year + "-" + month + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Export daily collection report to CSV format.
     * Requirements: 15.1, 15.4
     * 
     * TODO: Uncomment once ReportService.getDailyCollectionReport() is implemented
     */
    /*
    @GetMapping("/collection/daily/export/csv")
    public ResponseEntity<byte[]> exportDailyCollectionReportToCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> reportData = reportService.getDailyCollectionReport(date);
        byte[] csvData = reportService.exportReportToCSV("DAILY_COLLECTION", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "daily-collection-" + date + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }
    */

    /**
     * Export monthly collection report to CSV format.
     * Requirements: 15.1, 15.4
     * 
     * TODO: Uncomment once ReportService.getMonthlyCollectionReport() is implemented
     */
    /*
    @GetMapping("/collection/monthly/export/csv")
    public ResponseEntity<byte[]> exportMonthlyCollectionReportToCSV(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> reportData = reportService.getMonthlyCollectionReport(month, year);
        byte[] csvData = reportService.exportReportToCSV("MONTHLY_COLLECTION", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "monthly-collection-" + year + "-" + month + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }
    */

    /**
     * Export product-wise sales report to CSV format.
     * Requirements: 15.1, 15.4
     */
    @GetMapping("/product-wise-sales/export/csv")
    public ResponseEntity<byte[]> exportProductWiseSalesReportToCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> productData = reportService.getProductWiseSalesReport(startDate, endDate);
        
        // Wrap the list in a map for the export method
        Map<String, Object> reportData = Map.of("products", productData);
        byte[] csvData = reportService.exportReportToCSV("PRODUCT_WISE", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "product-wise-sales-" + startDate + "-to-" + endDate + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Export state-wise sales report to CSV format.
     * Requirements: 15.1, 15.4
     */
    @GetMapping("/state-wise-sales/export/csv")
    public ResponseEntity<byte[]> exportStateWiseSalesReportToCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getStateWiseSalesReport(startDate, endDate);
        byte[] csvData = reportService.exportReportToCSV("STATE_WISE", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "state-wise-sales-" + startDate + "-to-" + endDate + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Export city-wise sales report to CSV format.
     * Requirements: 15.1, 15.4
     */
    @GetMapping("/city-wise-sales/export/csv")
    public ResponseEntity<byte[]> exportCityWiseSalesReportToCSV(
            @RequestParam(required = false) String state,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getCityWiseSalesReport(state, startDate, endDate);
        byte[] csvData = reportService.exportReportToCSV("CITY_WISE", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        String filename = "city-wise-sales-" + (state != null ? state + "-" : "") + startDate + "-to-" + endDate + ".csv";
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Export salesperson sales report to CSV format.
     * Requirements: 15.1, 15.4
     */
    @GetMapping("/salesperson-sales/export/csv")
    public ResponseEntity<byte[]> exportSalespersonSalesReportToCSV(
            @RequestParam Long salespersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getSalespersonSalesReport(salespersonId, startDate, endDate);
        byte[] csvData = reportService.exportReportToCSV("SALESPERSON_SALES", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "salesperson-" + salespersonId + "-sales-" + startDate + "-to-" + endDate + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Generic export endpoint supporting multiple report types and formats.
     * Requirements: 15.1, 15.2, 15.3, 15.4
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam String reportType,
            @RequestParam String format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long salespersonId,
            @RequestParam(required = false) String state) {
        
        Map<String, Object> reportData = getReportData(reportType, startDate, endDate, salespersonId, state);
        byte[] exportData;
        String contentType;
        String fileExtension;
        
        switch (format.toUpperCase()) {
            case "CSV":
                exportData = reportService.exportReportToCSV(reportType.toUpperCase(), reportData);
                contentType = "text/csv";
                fileExtension = ".csv";
                break;
            case "EXCEL":
            case "XLSX":
                exportData = reportService.exportReportToExcel(reportType, reportData);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                fileExtension = ".xlsx";
                break;
            case "PDF":
                exportData = reportService.exportReportToPDF(reportType, reportData);
                contentType = "application/pdf";
                fileExtension = ".pdf";
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", reportType + "-" + startDate + "-to-" + endDate + fileExtension);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(exportData);
    }

    /**
     * Helper method to get report data based on report type.
     */
    private Map<String, Object> getReportData(String reportType, LocalDate startDate, LocalDate endDate, 
                                              Long salespersonId, String state) {
        switch (reportType.toUpperCase()) {
            case "PRODUCT_WISE":
                List<Map<String, Object>> products = reportService.getProductWiseSalesReport(startDate, endDate);
                return Map.of("products", products, "startDate", startDate.toString(), "endDate", endDate.toString());
            case "STATE_WISE":
                return reportService.getStateWiseSalesReport(startDate, endDate);
            case "CITY_WISE":
                return reportService.getCityWiseSalesReport(state, startDate, endDate);
            case "SALESPERSON_SALES":
                if (salespersonId == null) {
                    throw new IllegalArgumentException("salespersonId is required for SALESPERSON_SALES report");
                }
                return reportService.getSalespersonSalesReport(salespersonId, startDate, endDate);
            default:
                throw new IllegalArgumentException("Unsupported report type: " + reportType);
        }
    }

    // ==================== EXPORT - PDF ====================

    // ==================== EXPORT - PDF ====================

    /**
     * Export daily sales report to PDF format.
     * Requirements: 15.3, 15.4
     */
    @GetMapping("/daily-sales/export/pdf")
    public ResponseEntity<byte[]> exportDailySalesReportToPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> reportData = reportService.getDailySalesReport(date);
        byte[] pdfBytes = reportService.exportReportToPDF("daily-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "daily-sales-report-" + date + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Export monthly sales report to PDF format.
     * Requirements: 15.3, 15.4
     */
    @GetMapping("/monthly-sales/export/pdf")
    public ResponseEntity<byte[]> exportMonthlySalesReportToPDF(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> reportData = reportService.getMonthlySalesReport(month, year);
        byte[] pdfBytes = reportService.exportReportToPDF("monthly-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "monthly-sales-report-" + year + "-" + month + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Export daily collection report to PDF format.
     * Requirements: 15.3, 15.4
     * 
     * TODO: Uncomment once ReportService.getDailyCollectionReport() is implemented
     */
    /*
    @GetMapping("/collection/daily/export/pdf")
    public ResponseEntity<byte[]> exportDailyCollectionReportToPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> reportData = reportService.getDailyCollectionReport(date);
        byte[] pdfBytes = reportService.exportReportToPDF("daily-collection", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "daily-collection-report-" + date + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    */

    /**
     * Export monthly collection report to PDF format.
     * Requirements: 15.3, 15.4
     * 
     * TODO: Uncomment once ReportService.getMonthlyCollectionReport() is implemented
     */
    /*
    @GetMapping("/collection/monthly/export/pdf")
    public ResponseEntity<byte[]> exportMonthlyCollectionReportToPDF(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> reportData = reportService.getMonthlyCollectionReport(month, year);
        byte[] pdfBytes = reportService.exportReportToPDF("monthly-collection", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "monthly-collection-report-" + year + "-" + month + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    */

    /**
     * Export product-wise sales report to PDF format.
     * Requirements: 15.3, 15.4
     */
    @GetMapping("/product-wise-sales/export/pdf")
    public ResponseEntity<byte[]> exportProductWiseSalesReportToPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> products = reportService.getProductWiseSalesReport(startDate, endDate);
        
        Map<String, Object> reportData = Map.of(
            "startDate", startDate.toString(),
            "endDate", endDate.toString(),
            "products", products
        );
        
        byte[] pdfBytes = reportService.exportReportToPDF("product-wise", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "product-wise-sales-report-" + startDate + "-to-" + endDate + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Export state-wise sales report to PDF format.
     * Requirements: 15.3, 15.4
     */
    @GetMapping("/state-wise-sales/export/pdf")
    public ResponseEntity<byte[]> exportStateWiseSalesReportToPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getStateWiseSalesReport(startDate, endDate);
        byte[] pdfBytes = reportService.exportReportToPDF("state-wise", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "state-wise-sales-report-" + startDate + "-to-" + endDate + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Export city-wise sales report to PDF format.
     * Requirements: 15.3, 15.4
     */
    @GetMapping("/city-wise-sales/export/pdf")
    public ResponseEntity<byte[]> exportCityWiseSalesReportToPDF(
            @RequestParam(required = false) String state,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getCityWiseSalesReport(state, startDate, endDate);
        byte[] pdfBytes = reportService.exportReportToPDF("state-wise", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String filename = "city-wise-sales-report-" + (state != null ? state + "-" : "") + startDate + "-to-" + endDate + ".pdf";
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Export salesperson sales report to PDF format.
     * Requirements: 15.3, 15.4
     */
    @GetMapping("/salesperson-sales/export/pdf")
    public ResponseEntity<byte[]> exportSalespersonSalesReportToPDF(
            @RequestParam Long salespersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getSalespersonSalesReport(salespersonId, startDate, endDate);
        byte[] pdfBytes = reportService.exportReportToPDF("daily-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "salesperson-" + salespersonId + "-sales-" + startDate + "-to-" + endDate + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    // ==================== EXPORT - EXCEL ====================

    /**
     * Export daily sales report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/daily-sales/export/excel")
    public ResponseEntity<byte[]> exportDailySalesReportToExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> reportData = reportService.getDailySalesReport(date);
        byte[] excelData = reportService.exportReportToExcel("daily-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "daily-sales-" + date + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    /**
     * Export monthly sales report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/monthly-sales/export/excel")
    public ResponseEntity<byte[]> exportMonthlySalesReportToExcel(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> reportData = reportService.getMonthlySalesReport(month, year);
        byte[] excelData = reportService.exportReportToExcel("monthly-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "monthly-sales-" + year + "-" + String.format("%02d", month) + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    /**
     * Export daily collection report to Excel format.
     * Requirements: 15.2, 15.4
     * 
     * TODO: Uncomment once ReportService.getDailyCollectionReport() is implemented
     */
    /*
    @GetMapping("/collection/daily/export/excel")
    public ResponseEntity<byte[]> exportDailyCollectionReportToExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> reportData = reportService.getDailyCollectionReport(date);
        byte[] excelData = reportService.exportReportToExcel("daily-collection", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "daily-collection-" + date + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
    */

    /**
     * Export monthly collection report to Excel format.
     * Requirements: 15.2, 15.4
     * 
     * TODO: Uncomment once ReportService.getMonthlyCollectionReport() is implemented
     */
    /*
    @GetMapping("/collection/monthly/export/excel")
    public ResponseEntity<byte[]> exportMonthlyCollectionReportToExcel(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> reportData = reportService.getMonthlyCollectionReport(month, year);
        byte[] excelData = reportService.exportReportToExcel("monthly-collection", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "monthly-collection-" + year + "-" + String.format("%02d", month) + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
    */

    /**
     * Export product-wise sales report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/product-wise-sales/export/excel")
    public ResponseEntity<byte[]> exportProductWiseSalesReportToExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> productList = reportService.getProductWiseSalesReport(startDate, endDate);
        Map<String, Object> reportData = new java.util.HashMap<>();
        reportData.put("products", productList);
        reportData.put("startDate", startDate.toString());
        reportData.put("endDate", endDate.toString());
        
        byte[] excelData = reportService.exportReportToExcel("product-wise-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "product-wise-sales-" + startDate + "-to-" + endDate + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    /**
     * Export top-selling products report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/top-selling-products/export/excel")
    public ResponseEntity<byte[]> exportTopSellingProductsToExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> productList = reportService.getTopSellingProducts(startDate, endDate, limit);
        Map<String, Object> reportData = new java.util.HashMap<>();
        reportData.put("topProducts", productList);
        reportData.put("startDate", startDate.toString());
        reportData.put("endDate", endDate.toString());
        
        byte[] excelData = reportService.exportReportToExcel("top-selling-products", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "top-selling-products-" + startDate + "-to-" + endDate + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    /**
     * Export state-wise sales report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/state-wise-sales/export/excel")
    public ResponseEntity<byte[]> exportStateWiseSalesReportToExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getStateWiseSalesReport(startDate, endDate);
        byte[] excelData = reportService.exportReportToExcel("state-wise-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "state-wise-sales-" + startDate + "-to-" + endDate + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    /**
     * Export city-wise sales report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/city-wise-sales/export/excel")
    public ResponseEntity<byte[]> exportCityWiseSalesReportToExcel(
            @RequestParam(required = false) String state,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getCityWiseSalesReport(state, startDate, endDate);
        String filename = state != null ? 
                "city-wise-sales-" + state + "-" + startDate + "-to-" + endDate + ".xlsx" :
                "city-wise-sales-" + startDate + "-to-" + endDate + ".xlsx";
        byte[] excelData = reportService.exportReportToExcel("city-wise-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    /**
     * Export salesperson sales report to Excel format.
     * Requirements: 15.2, 15.4
     */
    @GetMapping("/salesperson-sales/export/excel")
    public ResponseEntity<byte[]> exportSalespersonSalesReportToExcel(
            @RequestParam Long salespersonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> reportData = reportService.getSalespersonSalesReport(salespersonId, startDate, endDate);
        byte[] excelData = reportService.exportReportToExcel("salesperson-sales", reportData);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "salesperson-" + salespersonId + "-sales-" + startDate + "-to-" + endDate + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
}
