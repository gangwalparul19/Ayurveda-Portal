package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    /**
     * Dashboard stats: today's orders, revenue, pending, monthly revenue, low stock, customers.
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        List<Order> todayOrders = orderRepository.findByOrderDate(today);
        stats.put("todayOrders", todayOrders.size());
        stats.put("todayRevenue", todayOrders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        stats.put("pendingOrders", orderRepository.countByStatusIn(
                List.of(Order.OrderStatus.NEW, Order.OrderStatus.CONFIRMED, 
                       Order.OrderStatus.PAID, Order.OrderStatus.PACKED)));

        // Monthly revenue
        LocalDate monthStart = today.withDayOfMonth(1);
        List<Order> monthOrders = orderRepository.findByOrderDateBetween(monthStart, today);
        stats.put("monthlyRevenue", monthOrders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // Low stock count - use optimized query instead of loading all products
        List<Product> lowStock = productRepository.findLowStockProducts();
        stats.put("lowStockProducts", lowStock.size());

        stats.put("totalCustomers", customerRepository.count());

        return stats;
    }

    /**
     * Daily report: order count, revenue, breakdown by status and payment mode.
     */
    public Map<String, Object> getDailyReport(LocalDate date) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Order> orders = orderRepository.findByOrderDate(date);

        report.put("date", date.toString());
        report.put("orderCount", orders.size());
        report.put("totalRevenue", orders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // By status
        Map<String, Long> byStatus = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getStatus().name(), Collectors.counting()));
        report.put("byStatus", byStatus);

        // By payment mode
        Map<String, Long> byPayment = orders.stream()
                .filter(o -> o.getPaymentMode() != null)
                .collect(Collectors.groupingBy(o -> o.getPaymentMode().name(), Collectors.counting()));
        report.put("byPaymentMode", byPayment);

        return report;
    }

    /**
     * Daily sales report as per Requirements 13.1, 13.2, 13.3.
     * Returns total orders, total sales amount (DELIVERED only), delivered count, cancelled count.
     */
    public Map<String, Object> getDailySalesReport(LocalDate date) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Order> orders = orderRepository.findByOrderDate(date);

        report.put("date", date.toString());
        report.put("totalOrders", orders.size());

        // Count delivered orders
        long deliveredCount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
        report.put("deliveredOrders", deliveredCount);

        // Count cancelled orders
        long cancelledCount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED)
                .count();
        report.put("cancelledOrders", cancelledCount);

        // Total sales amount - only DELIVERED orders (Requirement 13.3)
        BigDecimal totalSalesAmount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalSalesAmount", totalSalesAmount);

        return report;
    }

    /**
     * Monthly report: daily breakdown, totals, top products.
     */
    public Map<String, Object> getMonthlyReport(int month, int year) {
        Map<String, Object> report = new LinkedHashMap<>();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<Order> orders = orderRepository.findByOrderDateBetween(start, end);

        report.put("month", month);
        report.put("year", year);
        report.put("totalOrders", orders.size());
        report.put("totalRevenue", orders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // Daily breakdown
        Map<String, Long> dailyOrderCounts = orders.stream()
                .collect(Collectors.groupingBy(
                    o -> o.getOrderDate().toString(),
                    TreeMap::new,
                    Collectors.counting()));
        report.put("dailyBreakdown", dailyOrderCounts);

        // Top products by quantity sold
        Map<String, Integer> productQuantities = new HashMap<>();
        orders.forEach(order ->
                order.getItems().forEach(item -> {
                    String productName = item.getProductNameSnapshot();
                    productQuantities.merge(productName, item.getQuantity(), Integer::sum);
                })
        );
        
        List<Map<String, Object>> topProducts = productQuantities.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Map<String, Object> product = new LinkedHashMap<>();
                    product.put("productName", entry.getKey());
                    product.put("quantitySold", entry.getValue());
                    return product;
                })
                .collect(Collectors.toList());
        report.put("topProducts", topProducts);

        return report;
    }

    /**
     * Monthly sales report as per Requirements 13.1, 13.2, 13.3.
     * Returns total orders, total sales amount (DELIVERED only), delivered count, cancelled count.
     */
    public Map<String, Object> getMonthlySalesReport(int month, int year) {
        Map<String, Object> report = new LinkedHashMap<>();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<Order> orders = orderRepository.findByOrderDateBetween(start, end);

        report.put("month", month);
        report.put("year", year);
        report.put("totalOrders", orders.size());

        // Count delivered orders
        long deliveredCount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
        report.put("deliveredOrders", deliveredCount);

        // Count cancelled orders
        long cancelledCount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED)
                .count();
        report.put("cancelledOrders", cancelledCount);

        // Total sales amount - only DELIVERED orders (Requirement 13.3)
        BigDecimal totalSalesAmount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalSalesAmount", totalSalesAmount);

        return report;
    }

    /**
     * Salesperson performance report for a specific date range.
     */
    public List<Map<String, Object>> getSalespersonPerformance(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // Group orders by salesperson
        Map<Long, List<Order>> ordersBySalesperson = orders.stream()
                .filter(o -> o.getSalespersonId() != null)
                .collect(Collectors.groupingBy(Order::getSalespersonId));

        List<Map<String, Object>> performances = new ArrayList<>();
        
        ordersBySalesperson.forEach((salespersonId, salespersonOrders) -> {
            Map<String, Object> performance = new LinkedHashMap<>();
            performance.put("salespersonId", salespersonId);
            performance.put("totalOrders", salespersonOrders.size());
            
            BigDecimal totalRevenue = salespersonOrders.stream()
                    .map(Order::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            performance.put("totalRevenue", totalRevenue);
            
            // Average order value
            BigDecimal avgOrderValue = salespersonOrders.isEmpty() ? BigDecimal.ZERO :
                    totalRevenue.divide(BigDecimal.valueOf(salespersonOrders.size()), 2, BigDecimal.ROUND_HALF_UP);
            performance.put("averageOrderValue", avgOrderValue);
            
            // Orders by status
            Map<String, Long> ordersByStatus = salespersonOrders.stream()
                    .collect(Collectors.groupingBy(o -> o.getStatus().name(), Collectors.counting()));
            performance.put("ordersByStatus", ordersByStatus);
            
            performances.add(performance);
        });

        // Sort by revenue descending
        performances.sort((a, b) ->
                ((BigDecimal) b.get("totalRevenue")).compareTo((BigDecimal) a.get("totalRevenue")));

        return performances;
    }

    /**
     * Get salesperson sales report for a specific salesperson and date range.
     * Filters orders by salesperson ID and calculates sales metrics.
     * Requirements: 13.5, 20.5
     * 
     * @param salespersonId ID of the salesperson
     * @param startDate Start date of the range
     * @param endDate End date of the range
     * @return Sales report for the salesperson with metrics
     */
    public Map<String, Object> getSalespersonSalesReport(Long salespersonId, LocalDate startDate, LocalDate endDate) {
        // Filter orders by salesperson and date range
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate).stream()
                .filter(o -> o.getSalespersonId() != null && o.getSalespersonId().equals(salespersonId))
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("salespersonId", salespersonId);
        report.put("dateRange", Map.of("start", startDate.toString(), "end", endDate.toString()));
        report.put("totalOrders", orders.size());

        // Calculate total sales amount
        BigDecimal totalSalesAmount = orders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalSalesAmount", totalSalesAmount);

        // Count delivered orders
        long deliveredOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
        report.put("deliveredOrders", deliveredOrders);

        // Calculate delivered orders sales amount (only DELIVERED orders count in sales)
        BigDecimal deliveredSalesAmount = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("deliveredSalesAmount", deliveredSalesAmount);

        // Count cancelled orders
        long cancelledOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED)
                .count();
        report.put("cancelledOrders", cancelledOrders);

        // Average order value
        BigDecimal avgOrderValue = orders.isEmpty() ? BigDecimal.ZERO :
                totalSalesAmount.divide(BigDecimal.valueOf(orders.size()), 2, java.math.RoundingMode.HALF_UP);
        report.put("averageOrderValue", avgOrderValue);

        // Orders by status breakdown
        Map<String, Long> ordersByStatus = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getStatus().name(), Collectors.counting()));
        report.put("ordersByStatus", ordersByStatus);

        // Orders by payment mode breakdown
        Map<String, Long> ordersByPaymentMode = orders.stream()
                .filter(o -> o.getPaymentMode() != null)
                .collect(Collectors.groupingBy(o -> o.getPaymentMode().name(), Collectors.counting()));
        report.put("ordersByPaymentMode", ordersByPaymentMode);

        return report;
    }

    /**
     * Top products report by revenue and quantity for a date range.
     */
    public Map<String, Object> getTopProductsReport(LocalDate startDate, LocalDate endDate, int limit) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // Product metrics
        Map<String, ProductMetrics> productMetricsMap = new HashMap<>();

        orders.forEach(order ->
                order.getItems().forEach(item -> {
                    String productName = item.getProductNameSnapshot();
                    String sku = item.getSkuSnapshot();
                    
                    productMetricsMap.computeIfAbsent(productName, k -> new ProductMetrics(productName, sku));
                    
                    ProductMetrics metrics = productMetricsMap.get(productName);
                    metrics.quantitySold += item.getQuantity();
                    metrics.revenue = metrics.revenue.add(item.getLineTotal() != null ? 
                            item.getLineTotal() : BigDecimal.ZERO);
                    metrics.orderCount++;
                })
        );

        // Top by revenue
        List<Map<String, Object>> topByRevenue = productMetricsMap.values().stream()
                .sorted((a, b) -> b.revenue.compareTo(a.revenue))
                .limit(limit)
                .map(ProductMetrics::toMap)
                .collect(Collectors.toList());

        // Top by quantity
        List<Map<String, Object>> topByQuantity = productMetricsMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.quantitySold, a.quantitySold))
                .limit(limit)
                .map(ProductMetrics::toMap)
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("topByRevenue", topByRevenue);
        report.put("topByQuantity", topByQuantity);
        report.put("totalProducts", productMetricsMap.size());
        report.put("dateRange", Map.of("start", startDate.toString(), "end", endDate.toString()));

        return report;
    }

    /**
     * Customer analytics for a date range.
     */
    public Map<String, Object> getCustomerAnalytics(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        Map<String, Object> analytics = new LinkedHashMap<>();
        
        // Unique customers
        long uniqueCustomers = orders.stream()
                .filter(o -> o.getCustomer() != null)
                .map(o -> o.getCustomer().getId())
                .distinct()
                .count();
        analytics.put("uniqueCustomers", uniqueCustomers);
        
        // New vs returning customers (simple heuristic: orders before startDate)
        long newCustomers = orders.stream()
                .filter(o -> o.getCustomer() != null)
                .map(Order::getCustomer)
                .distinct()
                .filter(c -> orderRepository.findAllByCustomerId(c.getId(), 
                        org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements() == 1)
                .count();
        analytics.put("newCustomers", newCustomers);
        analytics.put("returningCustomers", uniqueCustomers - newCustomers);
        
        // Average order value
        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgOrderValue = orders.isEmpty() ? BigDecimal.ZERO :
                totalRevenue.divide(BigDecimal.valueOf(orders.size()), 2, BigDecimal.ROUND_HALF_UP);
        analytics.put("averageOrderValue", avgOrderValue);

        return analytics;
    }

    /**
     * State-wise sales report: aggregate sales by customer state.
     * Requirements: 14.3, 14.4
     */
    public Map<String, Object> getStateWiseSalesReport(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // Group orders by customer state
        Map<String, StateMetrics> stateMetricsMap = new HashMap<>();

        orders.forEach(order -> {
            if (order.getCustomer() != null && order.getCustomer().getState() != null) {
                String state = order.getCustomer().getState();
                
                stateMetricsMap.computeIfAbsent(state, k -> new StateMetrics(state));
                
                StateMetrics metrics = stateMetricsMap.get(state);
                metrics.orderCount++;
                
                if (order.getTotalAmount() != null) {
                    metrics.totalSales = metrics.totalSales.add(order.getTotalAmount());
                }
            }
        });

        // Convert to sorted list by total sales descending
        List<Map<String, Object>> stateReports = stateMetricsMap.values().stream()
                .sorted((a, b) -> b.totalSales.compareTo(a.totalSales))
                .map(StateMetrics::toMap)
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("states", stateReports);
        report.put("totalStates", stateMetricsMap.size());
        report.put("dateRange", Map.of("start", startDate.toString(), "end", endDate.toString()));

        return report;
    }

    /**
     * City-wise sales report with optional state filter.
     * Requirements: 14.3, 14.4
     */
    public Map<String, Object> getCityWiseSalesReport(String state, LocalDate startDate, LocalDate endDate) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // Filter by state if provided and group by city
        Map<String, CityMetrics> cityMetricsMap = new HashMap<>();

        orders.forEach(order -> {
            if (order.getCustomer() != null && order.getCustomer().getCity() != null) {
                // Apply state filter if specified
                if (state != null && !state.isEmpty()) {
                    if (!state.equalsIgnoreCase(order.getCustomer().getState())) {
                        return; // Skip orders not matching the state filter
                    }
                }
                
                String city = order.getCustomer().getCity();
                String customerState = order.getCustomer().getState();
                
                cityMetricsMap.computeIfAbsent(city, k -> new CityMetrics(city, customerState));
                
                CityMetrics metrics = cityMetricsMap.get(city);
                metrics.orderCount++;
                
                if (order.getTotalAmount() != null) {
                    metrics.totalSales = metrics.totalSales.add(order.getTotalAmount());
                }
            }
        });

        // Convert to sorted list by total sales descending
        List<Map<String, Object>> cityReports = cityMetricsMap.values().stream()
                .sorted((a, b) -> b.totalSales.compareTo(a.totalSales))
                .map(CityMetrics::toMap)
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cities", cityReports);
        report.put("totalCities", cityMetricsMap.size());
        report.put("stateFilter", state != null ? state : "All");
        report.put("dateRange", Map.of("start", startDate.toString(), "end", endDate.toString()));

        return report;
    }

    /**
     * Product-wise sales report aggregating by product for a date range.
     * Includes product name, total quantity sold, and total sales amount.
     * Requirements: 14.1, 14.2
     */
    public List<Map<String, Object>> getProductWiseSalesReport(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // Aggregate sales by product
        Map<String, ProductSalesMetrics> productSalesMap = new HashMap<>();

        orders.forEach(order ->
                order.getItems().forEach(item -> {
                    String productName = item.getProductNameSnapshot();
                    String sku = item.getSkuSnapshot();
                    
                    productSalesMap.computeIfAbsent(productName, 
                            k -> new ProductSalesMetrics(productName, sku));
                    
                    ProductSalesMetrics metrics = productSalesMap.get(productName);
                    metrics.totalQuantitySold += item.getQuantity();
                    metrics.totalSalesAmount = metrics.totalSalesAmount.add(
                            item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO);
                })
        );

        // Convert to list and sort by total sales amount descending
        return productSalesMap.values().stream()
                .sorted((a, b) -> b.totalSalesAmount.compareTo(a.totalSalesAmount))
                .map(ProductSalesMetrics::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Get top-selling products by quantity sold for a date range.
     * Requirements: 14.5
     * 
     * @param startDate Start date of the range
     * @param endDate End date of the range
     * @param limit Maximum number of products to return
     * @return List of top-selling products with metrics
     */
    public List<Map<String, Object>> getTopSellingProducts(LocalDate startDate, LocalDate endDate, Integer limit) {
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // Aggregate sales by product
        Map<String, ProductSalesMetrics> productSalesMap = new HashMap<>();

        orders.forEach(order ->
                order.getItems().forEach(item -> {
                    String productName = item.getProductNameSnapshot();
                    String sku = item.getSkuSnapshot();
                    
                    productSalesMap.computeIfAbsent(productName, 
                            k -> new ProductSalesMetrics(productName, sku));
                    
                    ProductSalesMetrics metrics = productSalesMap.get(productName);
                    metrics.totalQuantitySold += item.getQuantity();
                    metrics.totalSalesAmount = metrics.totalSalesAmount.add(
                            item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO);
                })
        );

        // Sort by quantity sold descending and limit results
        return productSalesMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.totalQuantitySold, a.totalQuantitySold))
                .limit(limit != null ? limit : 10) // Default to top 10 if limit not specified
                .map(ProductSalesMetrics::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Export report data to CSV format.
     * Requirements: 15.1, 15.4
     * 
     * @param reportType Type of report to export (DAILY_SALES, MONTHLY_SALES, PRODUCT_WISE, STATE_WISE, etc.)
     * @param reportData The report data to export
     * @return CSV data as byte array
     */
    public byte[] exportReportToCSV(String reportType, Map<String, Object> reportData) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        try {
            switch (reportType.toUpperCase()) {
                case "DAILY_SALES":
                case "MONTHLY_SALES":
                    exportSalesReportToCSV(writer, reportData);
                    break;
                case "PRODUCT_WISE":
                    exportProductWiseReportToCSV(writer, reportData);
                    break;
                case "STATE_WISE":
                    exportStateWiseReportToCSV(writer, reportData);
                    break;
                case "CITY_WISE":
                    exportCityWiseReportToCSV(writer, reportData);
                    break;
                case "SALESPERSON_SALES":
                    exportSalespersonSalesReportToCSV(writer, reportData);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported report type: " + reportType);
            }

            writer.flush();
            return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
        } finally {
            writer.close();
        }
    }

    /**
     * Export sales report (daily or monthly) to CSV format.
     */
    private void exportSalesReportToCSV(PrintWriter writer, Map<String, Object> reportData) {
        // Write header
        writer.println("Metric,Value");
        
        // Write report data
        if (reportData.containsKey("date")) {
            writer.println(escapeCsvValue("Date") + "," + escapeCsvValue(reportData.get("date")));
        }
        if (reportData.containsKey("month") && reportData.containsKey("year")) {
            writer.println(escapeCsvValue("Month") + "," + escapeCsvValue(reportData.get("month") + "/" + reportData.get("year")));
        }
        writer.println(escapeCsvValue("Total Orders") + "," + escapeCsvValue(reportData.get("totalOrders")));
        writer.println(escapeCsvValue("Delivered Orders") + "," + escapeCsvValue(reportData.get("deliveredOrders")));
        writer.println(escapeCsvValue("Cancelled Orders") + "," + escapeCsvValue(reportData.get("cancelledOrders")));
        writer.println(escapeCsvValue("Total Sales Amount") + "," + escapeCsvValue(reportData.get("totalSalesAmount")));
    }

    /**
     * Export product-wise report to CSV format.
     */
    private void exportProductWiseReportToCSV(PrintWriter writer, Map<String, Object> reportData) {
        // Write header
        writer.println("Product Name,SKU,Total Quantity Sold,Total Sales Amount");
        
        // reportData is expected to be a list of product metrics
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) reportData.get("products");
        
        if (products != null) {
            for (Map<String, Object> product : products) {
                writer.println(
                    escapeCsvValue(product.get("productName")) + "," +
                    escapeCsvValue(product.get("sku")) + "," +
                    escapeCsvValue(product.get("totalQuantitySold")) + "," +
                    escapeCsvValue(product.get("totalSalesAmount"))
                );
            }
        }
    }

    /**
     * Export state-wise report to CSV format.
     */
    private void exportStateWiseReportToCSV(PrintWriter writer, Map<String, Object> reportData) {
        // Write header
        writer.println("State Name,Order Count,Total Sales");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> states = (List<Map<String, Object>>) reportData.get("states");
        
        if (states != null) {
            for (Map<String, Object> state : states) {
                writer.println(
                    escapeCsvValue(state.get("stateName")) + "," +
                    escapeCsvValue(state.get("orderCount")) + "," +
                    escapeCsvValue(state.get("totalSales"))
                );
            }
        }
    }

    /**
     * Export city-wise report to CSV format.
     */
    private void exportCityWiseReportToCSV(PrintWriter writer, Map<String, Object> reportData) {
        // Write header
        writer.println("City Name,State Name,Order Count,Total Sales");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cities = (List<Map<String, Object>>) reportData.get("cities");
        
        if (cities != null) {
            for (Map<String, Object> city : cities) {
                writer.println(
                    escapeCsvValue(city.get("cityName")) + "," +
                    escapeCsvValue(city.get("stateName")) + "," +
                    escapeCsvValue(city.get("orderCount")) + "," +
                    escapeCsvValue(city.get("totalSales"))
                );
            }
        }
    }

    /**
     * Export salesperson sales report to CSV format.
     */
    private void exportSalespersonSalesReportToCSV(PrintWriter writer, Map<String, Object> reportData) {
        // Write header
        writer.println("Metric,Value");
        
        // Write report data
        writer.println(escapeCsvValue("Salesperson ID") + "," + escapeCsvValue(reportData.get("salespersonId")));
        
        @SuppressWarnings("unchecked")
        Map<String, String> dateRange = (Map<String, String>) reportData.get("dateRange");
        if (dateRange != null) {
            writer.println(escapeCsvValue("Date Range") + "," + 
                escapeCsvValue(dateRange.get("start") + " to " + dateRange.get("end")));
        }
        
        writer.println(escapeCsvValue("Total Orders") + "," + escapeCsvValue(reportData.get("totalOrders")));
        writer.println(escapeCsvValue("Total Sales Amount") + "," + escapeCsvValue(reportData.get("totalSalesAmount")));
        writer.println(escapeCsvValue("Delivered Orders") + "," + escapeCsvValue(reportData.get("deliveredOrders")));
        writer.println(escapeCsvValue("Delivered Sales Amount") + "," + escapeCsvValue(reportData.get("deliveredSalesAmount")));
        writer.println(escapeCsvValue("Cancelled Orders") + "," + escapeCsvValue(reportData.get("cancelledOrders")));
        writer.println(escapeCsvValue("Average Order Value") + "," + escapeCsvValue(reportData.get("averageOrderValue")));
    }

    /**
     * Escape CSV values to handle commas, quotes, and newlines properly.
     * Requirements: 15.1 - Proper CSV formatting
     */
    private String escapeCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        
        String stringValue = value.toString();
        
        // If the value contains comma, quote, or newline, wrap it in quotes and escape internal quotes
        if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
            // Escape quotes by doubling them
            stringValue = stringValue.replace("\"", "\"\"");
            // Wrap in quotes
            return "\"" + stringValue + "\"";
        }
        
        return stringValue;
    }

    // Helper class for product metrics
    private static class ProductMetrics {
        String productName;
        String sku;
        int quantitySold = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        int orderCount = 0;

        ProductMetrics(String productName, String sku) {
            this.productName = productName;
            this.sku = sku;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("productName", productName);
            map.put("sku", sku);
            map.put("quantitySold", quantitySold);
            map.put("revenue", revenue);
            map.put("orderCount", orderCount);
            return map;
        }
    }

    // Helper class for product-wise sales metrics
    private static class ProductSalesMetrics {
        String productName;
        String sku;
        int totalQuantitySold = 0;
        BigDecimal totalSalesAmount = BigDecimal.ZERO;

        ProductSalesMetrics(String productName, String sku) {
            this.productName = productName;
            this.sku = sku;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("productName", productName);
            map.put("sku", sku);
            map.put("totalQuantitySold", totalQuantitySold);
            map.put("totalSalesAmount", totalSalesAmount);
            return map;
        }
    }

    // Helper class for state-wise metrics
    private static class StateMetrics {
        String stateName;
        int orderCount = 0;
        BigDecimal totalSales = BigDecimal.ZERO;

        StateMetrics(String stateName) {
            this.stateName = stateName;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("stateName", stateName);
            map.put("orderCount", orderCount);
            map.put("totalSales", totalSales);
            return map;
        }
    }

    // Helper class for city-wise metrics
    private static class CityMetrics {
        String cityName;
        String stateName;
        int orderCount = 0;
        BigDecimal totalSales = BigDecimal.ZERO;

        CityMetrics(String cityName, String stateName) {
            this.cityName = cityName;
            this.stateName = stateName;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("cityName", cityName);
            map.put("stateName", stateName);
            map.put("orderCount", orderCount);
            map.put("totalSales", totalSales);
            return map;
        }
    }

    /**
     * Export report data to Excel (XLSX) format.
     * Requirements: 15.2, 15.4
     * 
     * @param reportType Type of report (e.g., "daily-sales", "monthly-sales", "product-wise", "state-wise")
     * @param reportData The report data to export (typically from other report methods)
     * @return byte array containing the XLSX file
     * @throws RuntimeException if Excel generation fails
     */
    public byte[] exportReportToExcel(String reportType, Map<String, Object> reportData) {
        try (Workbook workbook = new XSSFWorkbook(); 
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet(reportType);
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Create data style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            
            // Create currency style
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.cloneStyleFrom(dataStyle);
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("₹#,##0.00"));
            
            int rowNum = 0;
            
            // Add report title and metadata
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(formatReportTitle(reportType));
            titleCell.setCellStyle(headerStyle);
            
            // Add report date/period if available
            if (reportData.containsKey("date")) {
                Row dateRow = sheet.createRow(rowNum++);
                dateRow.createCell(0).setCellValue("Date: " + reportData.get("date"));
            } else if (reportData.containsKey("startDate") && reportData.containsKey("endDate")) {
                Row dateRow = sheet.createRow(rowNum++);
                dateRow.createCell(0).setCellValue("Period: " + reportData.get("startDate") + " to " + reportData.get("endDate"));
            }
            
            // Add empty row for spacing
            rowNum++;
            
            // Process based on report type
            if (reportType.contains("product-wise") || reportType.contains("top-selling")) {
                rowNum = addProductReportData(sheet, reportData, rowNum, headerStyle, dataStyle, currencyStyle);
            } else if (reportType.contains("state-wise")) {
                rowNum = addStateReportData(sheet, reportData, rowNum, headerStyle, dataStyle, currencyStyle);
            } else if (reportType.contains("city-wise")) {
                rowNum = addCityReportData(sheet, reportData, rowNum, headerStyle, dataStyle, currencyStyle);
            } else if (reportType.contains("sales")) {
                rowNum = addSalesReportData(sheet, reportData, rowNum, headerStyle, dataStyle, currencyStyle);
            } else {
                // Generic report format - just add key-value pairs
                rowNum = addGenericReportData(sheet, reportData, rowNum, headerStyle, dataStyle, currencyStyle);
            }
            
            // Auto-size columns
            for (int i = 0; i < 10; i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Exception e) {
                    // Ignore if column doesn't exist
                }
            }
            
            workbook.write(out);
            return out.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating Excel report", e);
            throw new RuntimeException("Failed to generate Excel report: " + e.getMessage(), e);
        }
    }
    
    /**
     * Format report type into a readable title.
     */
    private String formatReportTitle(String reportType) {
        if (reportType == null) return "Report";
        return Arrays.stream(reportType.split("-"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" ")) + " Report";
    }
    
    /**
     * Add product report data to Excel sheet.
     */
    private int addProductReportData(Sheet sheet, Map<String, Object> reportData, 
                                     int startRow, CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {
        List<?> products = null;
        
        if (reportData.containsKey("products")) {
            products = (List<?>) reportData.get("products");
        } else if (reportData.containsKey("topProducts")) {
            products = (List<?>) reportData.get("topProducts");
        }
        
        if (products == null || products.isEmpty()) {
            return startRow;
        }
        
        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {"Product Name", "SKU", "Quantity Sold", "Total Sales Amount"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Add data rows
        for (Object obj : products) {
            Map<String, Object> product = (Map<String, Object>) obj;
            Row row = sheet.createRow(startRow++);
            
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(String.valueOf(product.get("productName")));
            cell0.setCellStyle(dataStyle);
            
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(String.valueOf(product.get("sku")));
            cell1.setCellStyle(dataStyle);
            
            Cell cell2 = row.createCell(2);
            Object qty = product.get("totalQuantitySold");
            if (qty == null) qty = product.get("quantitySold");
            cell2.setCellValue(qty != null ? ((Number) qty).intValue() : 0);
            cell2.setCellStyle(dataStyle);
            
            Cell cell3 = row.createCell(3);
            Object sales = product.get("totalSalesAmount");
            if (sales == null) sales = product.get("revenue");
            if (sales instanceof BigDecimal) {
                cell3.setCellValue(((BigDecimal) sales).doubleValue());
                cell3.setCellStyle(currencyStyle);
            } else {
                cell3.setCellValue(0.0);
                cell3.setCellStyle(currencyStyle);
            }
        }
        
        return startRow;
    }
    
    /**
     * Add state-wise report data to Excel sheet.
     */
    private int addStateReportData(Sheet sheet, Map<String, Object> reportData, 
                                   int startRow, CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {
        List<?> states = (List<?>) reportData.get("states");
        if (states == null || states.isEmpty()) {
            return startRow;
        }
        
        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {"State", "Order Count", "Total Sales"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Add data rows
        for (Object obj : states) {
            Map<String, Object> state = (Map<String, Object>) obj;
            Row row = sheet.createRow(startRow++);
            
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(String.valueOf(state.get("stateName")));
            cell0.setCellStyle(dataStyle);
            
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(((Number) state.get("orderCount")).intValue());
            cell1.setCellStyle(dataStyle);
            
            Cell cell2 = row.createCell(2);
            BigDecimal totalSales = (BigDecimal) state.get("totalSales");
            cell2.setCellValue(totalSales.doubleValue());
            cell2.setCellStyle(currencyStyle);
        }
        
        return startRow;
    }
    
    /**
     * Add city-wise report data to Excel sheet.
     */
    private int addCityReportData(Sheet sheet, Map<String, Object> reportData, 
                                  int startRow, CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {
        List<?> cities = (List<?>) reportData.get("cities");
        if (cities == null || cities.isEmpty()) {
            return startRow;
        }
        
        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {"City", "State", "Order Count", "Total Sales"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Add data rows
        for (Object obj : cities) {
            Map<String, Object> city = (Map<String, Object>) obj;
            Row row = sheet.createRow(startRow++);
            
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(String.valueOf(city.get("cityName")));
            cell0.setCellStyle(dataStyle);
            
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(String.valueOf(city.get("stateName")));
            cell1.setCellStyle(dataStyle);
            
            Cell cell2 = row.createCell(2);
            cell2.setCellValue(((Number) city.get("orderCount")).intValue());
            cell2.setCellStyle(dataStyle);
            
            Cell cell3 = row.createCell(3);
            BigDecimal totalSales = (BigDecimal) city.get("totalSales");
            cell3.setCellValue(totalSales.doubleValue());
            cell3.setCellStyle(currencyStyle);
        }
        
        return startRow;
    }
    
    /**
     * Add sales report data to Excel sheet.
     */
    private int addSalesReportData(Sheet sheet, Map<String, Object> reportData, 
                                   int startRow, CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {
        // Create summary section
        Row headerRow = sheet.createRow(startRow++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Summary");
        headerCell.setCellStyle(headerStyle);
        
        // Add summary metrics
        String[] metricKeys = {"totalOrders", "totalSalesAmount", "deliveredOrders", "cancelledOrders"};
        String[] metricLabels = {"Total Orders", "Total Sales Amount", "Delivered Orders", "Cancelled Orders"};
        
        for (int i = 0; i < metricKeys.length; i++) {
            if (reportData.containsKey(metricKeys[i])) {
                Row row = sheet.createRow(startRow++);
                
                Cell labelCell = row.createCell(0);
                labelCell.setCellValue(metricLabels[i]);
                labelCell.setCellStyle(dataStyle);
                
                Cell valueCell = row.createCell(1);
                Object value = reportData.get(metricKeys[i]);
                if (value instanceof BigDecimal) {
                    valueCell.setCellValue(((BigDecimal) value).doubleValue());
                    valueCell.setCellStyle(currencyStyle);
                } else if (value instanceof Number) {
                    valueCell.setCellValue(((Number) value).doubleValue());
                    valueCell.setCellStyle(dataStyle);
                } else {
                    valueCell.setCellValue(String.valueOf(value));
                    valueCell.setCellStyle(dataStyle);
                }
            }
        }
        
        return startRow;
    }
    
    /**
     * Add generic report data to Excel sheet.
     */
    private int addGenericReportData(Sheet sheet, Map<String, Object> reportData, 
                                     int startRow, CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle) {
        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        Cell cell0 = headerRow.createCell(0);
        cell0.setCellValue("Metric");
        cell0.setCellStyle(headerStyle);
        Cell cell1 = headerRow.createCell(1);
        cell1.setCellValue("Value");
        cell1.setCellStyle(headerStyle);
        
        // Add all key-value pairs from report data
        for (Map.Entry<String, Object> entry : reportData.entrySet()) {
            // Skip complex nested objects
            if (entry.getValue() instanceof List || entry.getValue() instanceof Map) {
                continue;
            }
            
            Row row = sheet.createRow(startRow++);
            
            Cell labelCell = row.createCell(0);
            labelCell.setCellValue(entry.getKey());
            labelCell.setCellStyle(dataStyle);
            
            Cell valueCell = row.createCell(1);
            Object value = entry.getValue();
            if (value instanceof BigDecimal) {
                valueCell.setCellValue(((BigDecimal) value).doubleValue());
                valueCell.setCellStyle(currencyStyle);
            } else if (value instanceof Number) {
                valueCell.setCellValue(((Number) value).doubleValue());
                valueCell.setCellStyle(dataStyle);
            } else {
                valueCell.setCellValue(String.valueOf(value));
                valueCell.setCellStyle(dataStyle);
            }
        }
        
        return startRow;
    }

    /**
     * Export report to PDF format - Requirements 15.3, 15.4
     */
    public byte[] exportReportToPDF(String reportType, Map<String, Object> reportData) {
        log.info("Generating PDF export for report type: {}", reportType);
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4, 36, 36, 36, 36);
            com.lowagie.text.pdf.PdfWriter.getInstance(document, outputStream);
            document.open();
            
            // Add content based on report type
            com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font normalFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL);
            
            // Title
            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph(reportType.toUpperCase().replace("-", " ") + " REPORT", titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);
            
            // Add report data as table
            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(2);
            table.setWidthPercentage(100);
            
            for (Map.Entry<String, Object> entry : reportData.entrySet()) {
                if (!(entry.getValue() instanceof List || entry.getValue() instanceof Map)) {
                    com.lowagie.text.pdf.PdfPCell keyCell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(entry.getKey(), normalFont));
                    keyCell.setPadding(5);
                    table.addCell(keyCell);
                    
                    String value = entry.getValue() != null ? entry.getValue().toString() : "N/A";
                    com.lowagie.text.pdf.PdfPCell valueCell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(value, normalFont));
                    valueCell.setPadding(5);
                    table.addCell(valueCell);
                }
            }
            
            document.add(table);
            
            // Footer
            com.lowagie.text.Font footerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.ITALIC);
            com.lowagie.text.Paragraph footer = new com.lowagie.text.Paragraph("Generated on: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), footerFont);
            footer.setAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
            footer.setSpacingBefore(20);
            document.add(footer);
            
            document.close();
            
            byte[] pdfBytes = outputStream.toByteArray();
            log.info("PDF export generated successfully, size: {} bytes", pdfBytes.length);
            return pdfBytes;
            
        } catch (com.lowagie.text.DocumentException | java.io.IOException e) {
            log.error("Error generating PDF export for report type {}: {}", reportType, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF export for " + reportType, e);
        }
    }
}
