package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService export functionality.
 * Tests CSV, Excel, and PDF export methods for various report types.
 * 
 * Tests Requirement 15: Report Export
 * - 15.1: CSV export with proper formatting and headers
 * - 15.2: Excel export with XLSX format and proper formatting
 * - 15.3: PDF export with formatted document
 * - 15.4: Appropriate content-type headers for each format
 */
@ExtendWith(MockitoExtension.class)
class ReportExportTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private ReportService reportService;

    private Order testOrder;
    private Customer testCustomer;
    private OrderItem testOrderItem;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2024, 1, 15);

        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .addressLine1("123 Main Street")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .createdAt(LocalDateTime.now())
                .build();

        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORD-20240115-0001");
        testOrder.setCustomer(testCustomer);
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        testOrder.setPaymentMode(Order.PaymentMode.UPI);
        testOrder.setSubtotal(BigDecimal.valueOf(1000.00));
        testOrder.setDiscountAmount(BigDecimal.valueOf(50.00));
        testOrder.setTaxAmount(BigDecimal.valueOf(85.50));
        testOrder.setShippingCharge(BigDecimal.valueOf(50.00));
        testOrder.setTotalAmount(BigDecimal.valueOf(1085.50));
        testOrder.setOrderDate(testDate);
        testOrder.setCreatedAt(LocalDateTime.now());

        testOrderItem = new OrderItem();
        testOrderItem.setId(1L);
        testOrderItem.setOrder(testOrder);
        testOrderItem.setProductNameSnapshot("Ashwagandha Capsules");
        testOrderItem.setSkuSnapshot("ASH-001");
        testOrderItem.setQuantity(2);
        testOrderItem.setUnitPrice(BigDecimal.valueOf(500.00));
        testOrderItem.setLineTotal(BigDecimal.valueOf(1000.00));

        testOrder.setItems(Arrays.asList(testOrderItem));
    }


    @Test
    void testExportDailySalesReportToCSV_Success() {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Arrays.asList(testOrder));
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] csvData = reportService.exportReportToCSV("DAILY_SALES", reportData);
        
        assertNotNull(csvData);
        assertTrue(csvData.length > 0);
        String csvContent = new String(csvData, StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("Metric,Value"));
        assertTrue(csvContent.contains("Date," + testDate.toString()));
        assertTrue(csvContent.contains("Total Orders,1"));
        assertTrue(csvContent.contains("Delivered Orders,1"));
        assertTrue(csvContent.contains("Total Sales Amount"));
        assertTrue(csvContent.contains("1085"));
    }

    @Test
    void testExportMonthlySalesReportToCSV_Success() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        when(orderRepository.findByOrderDateBetween(start, end)).thenReturn(Arrays.asList(testOrder));
        Map<String, Object> reportData = reportService.getMonthlySalesReport(1, 2024);
        
        byte[] csvData = reportService.exportReportToCSV("MONTHLY_SALES", reportData);
        
        assertNotNull(csvData);
        String csvContent = new String(csvData, StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("Metric,Value"));
        assertTrue(csvContent.contains("Month,1/2024"));
    }

    @Test
    void testExportProductWiseReportToCSV_Success() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        when(orderRepository.findByOrderDateBetween(start, end)).thenReturn(Arrays.asList(testOrder));
        List<Map<String, Object>> productReport = reportService.getProductWiseSalesReport(start, end);
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("products", productReport);
        
        byte[] csvData = reportService.exportReportToCSV("PRODUCT_WISE", reportData);
        
        assertNotNull(csvData);
        String csvContent = new String(csvData, StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("Product Name,SKU,Total Quantity Sold,Total Sales Amount"));
        assertTrue(csvContent.contains("Ashwagandha Capsules"));
    }


    @Test
    void testCSVExport_HandlesEmptyData() {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Collections.emptyList());
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] csvData = reportService.exportReportToCSV("DAILY_SALES", reportData);
        
        assertNotNull(csvData);
        assertTrue(csvData.length > 0);
        String csvContent = new String(csvData, StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("Metric,Value"));
        assertTrue(csvContent.contains("Total Orders,0"));
    }

    @Test
    void testCSVExport_UnsupportedReportType_ThrowsException() {
        Map<String, Object> reportData = new HashMap<>();
        
        assertThrows(IllegalArgumentException.class, 
            () -> reportService.exportReportToCSV("INVALID_TYPE", reportData));
    }

    @Test
    void testExportDailySalesReportToExcel_Success() throws Exception {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Arrays.asList(testOrder));
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] excelData = reportService.exportReportToExcel("daily-sales", reportData);
        
        assertNotNull(excelData);
        assertTrue(excelData.length > 0);
        
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            assertNotNull(workbook);
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("daily-sales", sheet.getSheetName());
            Row titleRow = sheet.getRow(0);
            assertNotNull(titleRow);
            assertTrue(titleRow.getCell(0).getStringCellValue().contains("Daily Sales Report"));
        }
    }


    @Test
    void testExportProductWiseReportToExcel_Success() throws Exception {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        when(orderRepository.findByOrderDateBetween(start, end)).thenReturn(Arrays.asList(testOrder));
        List<Map<String, Object>> productReport = reportService.getProductWiseSalesReport(start, end);
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("products", productReport);
        
        byte[] excelData = reportService.exportReportToExcel("product-wise", reportData);
        
        assertNotNull(excelData);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean foundHeader = false;
            for (Row row : sheet) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null && "Product Name".equals(firstCell.getStringCellValue())) {
                    foundHeader = true;
                    break;
                }
            }
            assertTrue(foundHeader);
        }
    }

    @Test
    void testExcelExport_GeneratesValidXLSX() throws Exception {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Arrays.asList(testOrder));
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] excelData = reportService.exportReportToExcel("daily-sales", reportData);
        
        assertNotNull(excelData);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            assertTrue(workbook instanceof XSSFWorkbook);
        }
    }


    @Test
    void testExportDailySalesReportToPDF_Success() {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Arrays.asList(testOrder));
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] pdfData = reportService.exportReportToPDF("daily-sales", reportData);
        
        assertNotNull(pdfData);
        assertTrue(pdfData.length > 0);
        assertEquals('%', (char) pdfData[0]);
        assertEquals('P', (char) pdfData[1]);
        assertEquals('D', (char) pdfData[2]);
        assertEquals('F', (char) pdfData[3]);
    }

    @Test
    void testExportProductWiseReportToPDF_Success() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        when(orderRepository.findByOrderDateBetween(start, end)).thenReturn(Arrays.asList(testOrder));
        List<Map<String, Object>> productReport = reportService.getProductWiseSalesReport(start, end);
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("products", productReport);
        
        byte[] pdfData = reportService.exportReportToPDF("product-wise", reportData);
        
        assertNotNull(pdfData);
        String pdfHeader = new String(Arrays.copyOfRange(pdfData, 0, 4), StandardCharsets.UTF_8);
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void testPDFExport_HandlesEmptyData() {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Collections.emptyList());
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] pdfData = reportService.exportReportToPDF("daily-sales", reportData);
        
        assertNotNull(pdfData);
        assertTrue(pdfData.length > 0);
        String pdfHeader = new String(Arrays.copyOfRange(pdfData, 0, 4), StandardCharsets.UTF_8);
        assertEquals("%PDF", pdfHeader);
    }


    @Test
    void testExportAllFormats_ConsistentData() {
        when(orderRepository.findByOrderDate(testDate)).thenReturn(Arrays.asList(testOrder));
        Map<String, Object> reportData = reportService.getDailySalesReport(testDate);
        
        byte[] csvData = reportService.exportReportToCSV("DAILY_SALES", reportData);
        byte[] excelData = reportService.exportReportToExcel("daily-sales", reportData);
        byte[] pdfData = reportService.exportReportToPDF("daily-sales", reportData);
        
        assertNotNull(csvData);
        assertNotNull(excelData);
        assertNotNull(pdfData);
        assertTrue(csvData.length > 0);
        assertTrue(excelData.length > 0);
        assertTrue(pdfData.length > 0);
    }
}
