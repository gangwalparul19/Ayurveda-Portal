package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.InvoiceDTO;
import com.ayurveda.platform.dto.response.InvoiceItemDTO;
import com.ayurveda.platform.dto.response.VyaparInvoiceDTO;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.entity.CompanyConfig;
import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.entity.BillingExport;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.PaymentRecord;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.BillingExportRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BillingExportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final OrderRepository orderRepository;
    private final BillingExportRepository billingExportRepository;
    private final ConfigurationService configurationService;

    /**
     * Export orders to Vyapar-compatible CSV format.
     * Columns: Invoice No, Date, Customer, Phone, Item, Qty, Rate, Amount, Tax, Total
     */
    public byte[] exportVyaparCsv(LocalDate from, LocalDate to, Long userId) {
        log.info("Exporting Vyapar CSV for date range: {} to {}, requested by user: {}", from, to, userId);
        List<Order> orders = orderRepository.findByOrderDateBetween(from, to);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);

        // Header
        writer.println("Invoice No,Date,Customer Name,Phone,Product,SKU,Qty,Rate,Discount,Tax,Line Total,Order Total,Payment Mode,Payment Status");

        int exportedOrders = 0;
        for (Order order : orders) {
            if (order.getStatus() == Order.OrderStatus.CANCELLED) continue;
            exportedOrders++;

            for (OrderItem item : order.getItems()) {
                writer.printf("%s,%s,%s,%s,%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s%n",
                    order.getOrderNumber(),
                    order.getOrderDate(),
                    order.getCustomer() != null ? escapeCsv(order.getCustomer().getName()) : "",
                    order.getCustomer() != null ? order.getCustomer().getPhone() : "",
                    escapeCsv(item.getProductNameSnapshot()),
                    item.getSkuSnapshot(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO,
                    item.getTaxAmount() != null ? item.getTaxAmount() : BigDecimal.ZERO,
                    item.getLineTotal(),
                    order.getTotalAmount(),
                    order.getPaymentMode() != null ? order.getPaymentMode().name() : "",
                    order.getPaymentStatus() != null ? order.getPaymentStatus().name() : ""
                );
            }
        }

        writer.flush();

        // Log the export
        BillingExport export = new BillingExport();
        export.setExportType(BillingExport.ExportType.VYAPAR_CSV);
        export.setDateRangeStart(from);
        export.setDateRangeEnd(to);
        export.setRecordCount(exportedOrders);
        export.setGeneratedBy(userId);
        export.setGeneratedAt(LocalDateTime.now());
        billingExportRepository.save(export);

        log.info("Vyapar CSV export completed: {} orders exported", exportedOrders);
        return baos.toByteArray();
    }

    /**
     * Export GST summary as JSON-compatible data.
     */
    public byte[] exportGstJson(LocalDate from, LocalDate to, Long userId) {
        log.info("Exporting GST JSON for date range: {} to {}, requested by user: {}", from, to, userId);
        List<Order> orders = orderRepository.findByOrderDateBetween(from, to);

        StringBuilder json = new StringBuilder();
        json.append("[\n");

        int exportedOrders = 0;
        boolean first = true;
        for (Order order : orders) {
            if (order.getStatus() == Order.OrderStatus.CANCELLED) continue;
            exportedOrders++;
            if (!first) json.append(",\n");
            first = false;

            json.append(String.format(
                "  {\"invoiceNo\":\"%s\",\"date\":\"%s\",\"customer\":\"%s\",\"gstin\":\"%s\"," +
                "\"subtotal\":%.2f,\"tax\":%.2f,\"total\":%.2f}",
                order.getOrderNumber(),
                order.getOrderDate(),
                order.getCustomer() != null ? order.getCustomer().getName() : "",
                order.getCustomer() != null && order.getCustomer().getGstin() != null
                    ? order.getCustomer().getGstin() : "",
                order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO,
                order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO,
                order.getTotalAmount()
            ));
        }

        json.append("\n]");

        BillingExport export = new BillingExport();
        export.setExportType(BillingExport.ExportType.GST_JSON);
        export.setDateRangeStart(from);
        export.setDateRangeEnd(to);
        export.setRecordCount(exportedOrders);
        export.setGeneratedBy(userId);
        export.setGeneratedAt(LocalDateTime.now());
        billingExportRepository.save(export);

        log.info("GST JSON export completed: {} orders exported", exportedOrders);
        return json.toString().getBytes();
    }

    public List<BillingExport> getExportHistory() {
        return billingExportRepository.findAll();
    }

    /**
     * Maps an Order entity to Vyapar-compatible invoice format.
     * Implements Requirement 16.1: Map order data to Vyapar CSV format
     * 
     * This method extracts all necessary data from the Order entity and its relationships
     * (Customer, OrderItems) and structures it into a VyaparInvoiceDTO ready for export.
     * 
     * @param order The Order entity to map
     * @return VyaparInvoiceDTO containing all data in Vyapar-compatible format
     */
    public VyaparInvoiceDTO mapOrderToVyaparFormat(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        // Map customer information
        Customer customer = order.getCustomer();
        String customerName = customer != null ? customer.getName() : "";
        String customerPhone = customer != null ? customer.getPhone() : "";
        String customerEmail = customer != null ? customer.getEmail() : "";
        String city = customer != null ? customer.getCity() : "";
        String state = customer != null ? customer.getState() : "";
        String pincode = customer != null ? customer.getPincode() : "";
        String gstin = customer != null ? customer.getGstin() : "";
        
        // Concatenate shipping address from address lines
        String shippingAddress = buildShippingAddress(customer);

        // Map order items to line items
        List<VyaparInvoiceDTO.VyaparInvoiceLineItemDTO> lineItems = order.getItems().stream()
                .map(this::mapOrderItemToLineItem)
                .collect(Collectors.toList());

        // Build and return the Vyapar invoice DTO
        return VyaparInvoiceDTO.builder()
                .invoiceNumber(order.getOrderNumber())
                .invoiceDate(order.getOrderDate())
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .shippingAddress(shippingAddress)
                .city(city)
                .state(state)
                .pincode(pincode)
                .gstin(gstin)
                .lineItems(lineItems)
                .subtotal(order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO)
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .taxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO)
                .shippingCharge(order.getShippingCharge() != null ? order.getShippingCharge() : BigDecimal.ZERO)
                .totalAmount(order.getTotalAmount())
                .paymentMode(order.getPaymentMode() != null ? order.getPaymentMode().name() : "")
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "")
                .orderSource(order.getOrderSource() != null ? order.getOrderSource().name() : "")
                .orderStatus(order.getStatus() != null ? order.getStatus().name() : "")
                .build();
    }

    /**
     * Maps an OrderItem entity to a Vyapar invoice line item DTO.
     * 
     * @param item The OrderItem to map
     * @return VyaparInvoiceLineItemDTO with product details
     */
    private VyaparInvoiceDTO.VyaparInvoiceLineItemDTO mapOrderItemToLineItem(OrderItem item) {
        return VyaparInvoiceDTO.VyaparInvoiceLineItemDTO.builder()
                .productName(item.getProductNameSnapshot())
                .sku(item.getSkuSnapshot())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .mrp(item.getMrpSnapshot())
                .discount(item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO)
                .taxAmount(item.getTaxAmount() != null ? item.getTaxAmount() : BigDecimal.ZERO)
                .lineTotal(item.getLineTotal())
                .build();
    }

    /**
     * Export selected orders to Vyapar-compatible CSV format.
     * Implements Requirement 16.2: Export orders in Vyapar-compatible CSV format
     * 
     * @param orderIds List of order IDs to export
     * @return CSV file as byte array
     */
    public byte[] exportToVyaparFormat(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            log.warn("No order IDs provided for export");
            return new byte[0];
        }
        
        log.info("Exporting {} orders to Vyapar format", orderIds.size());

        List<Order> orders = orderRepository.findAllById(orderIds);
        
        if (orders.isEmpty()) {
            log.warn("No orders found for provided IDs");
            return new byte[0];
        }

        return generateVyaparCsvFromOrders(orders);
    }

    /**
     * Export all orders for a specific date to Vyapar-compatible CSV format.
     * Implements Requirement 16.3: Support daily order exports for a specific date
     * 
     * @param date The date to export orders for
     * @return CSV file as byte array
     */
    public byte[] exportDailyOrdersToVyapar(LocalDate date) {
        log.info("Exporting daily orders to Vyapar format for date: {}", date);
        
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        List<Order> orders = orderRepository.findByOrderDate(date);
        
        if (orders.isEmpty()) {
            log.info("No orders found for date: {}", date);
            return new byte[0];
        }

        log.info("Found {} orders for date: {}", orders.size(), date);
        return generateVyaparCsvFromOrders(orders);
    }

    /**
     * Export all orders for a specific month to Vyapar-compatible CSV format.
     * Implements Requirement 16.3: Support monthly order exports for a specific month
     * 
     * @param month The year-month to export orders for
     * @return CSV file as byte array
     */
    public byte[] exportMonthlyOrdersToVyapar(YearMonth month) {
        log.info("Exporting monthly orders to Vyapar format for month: {}", month);
        
        if (month == null) {
            throw new IllegalArgumentException("Month cannot be null");
        }

        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();
        
        List<Order> orders = orderRepository.findByOrderDateBetween(startDate, endDate);
        
        if (orders.isEmpty()) {
            log.info("No orders found for month: {}", month);
            return new byte[0];
        }

        log.info("Found {} orders for month: {}", orders.size(), month);
        return generateVyaparCsvFromOrders(orders);
    }

    /**
     * Generates Vyapar-compatible CSV from a list of orders.
     * Implements Requirement 16.4: Include all mapped data in the export file
     * 
     * The CSV format includes:
     * - Invoice Number, Date
     * - Customer Name, Phone, Email, Address, City, State, Pincode, GSTIN
     * - Product Name, SKU, Quantity, Unit Price, MRP, Discount, Tax, Line Total
     * - Subtotal, Total Discount, Total Tax, Shipping Charge, Total Amount
     * - Payment Mode, Payment Status
     * 
     * @param orders List of orders to export
     * @return CSV file as byte array
     */
    private byte[] generateVyaparCsvFromOrders(List<Order> orders) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);

        // Write CSV header with all Vyapar-compatible columns
        writer.println("Invoice No,Invoice Date,Customer Name,Customer Phone,Customer Email," +
                "Shipping Address,City,State,Pincode,GSTIN," +
                "Product Name,SKU,Quantity,Unit Price,MRP,Discount,Tax,Line Total," +
                "Subtotal,Total Discount,Total Tax,Shipping Charge,Total Amount," +
                "Payment Mode,Payment Status,Order Source,Order Status");

        int exportedOrders = 0;
        int totalLineItems = 0;

        for (Order order : orders) {
            // Skip cancelled orders
            if (order.getStatus() == Order.OrderStatus.CANCELLED) {
                log.debug("Skipping cancelled order: {}", order.getOrderNumber());
                continue;
            }

            // Map order to Vyapar format
            VyaparInvoiceDTO vyaparInvoice = mapOrderToVyaparFormat(order);
            exportedOrders++;

            // Write one CSV row per line item
            for (VyaparInvoiceDTO.VyaparInvoiceLineItemDTO lineItem : vyaparInvoice.getLineItems()) {
                totalLineItems++;
                
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,%s%n",
                        escapeCsv(vyaparInvoice.getInvoiceNumber()),
                        vyaparInvoice.getInvoiceDate(),
                        escapeCsv(vyaparInvoice.getCustomerName()),
                        escapeCsv(vyaparInvoice.getCustomerPhone()),
                        escapeCsv(vyaparInvoice.getCustomerEmail()),
                        escapeCsv(vyaparInvoice.getShippingAddress()),
                        escapeCsv(vyaparInvoice.getCity()),
                        escapeCsv(vyaparInvoice.getState()),
                        escapeCsv(vyaparInvoice.getPincode()),
                        escapeCsv(vyaparInvoice.getGstin()),
                        escapeCsv(lineItem.getProductName()),
                        escapeCsv(lineItem.getSku()),
                        lineItem.getQuantity(),
                        lineItem.getUnitPrice(),
                        lineItem.getMrp(),
                        lineItem.getDiscount(),
                        lineItem.getTaxAmount(),
                        lineItem.getLineTotal(),
                        vyaparInvoice.getSubtotal(),
                        vyaparInvoice.getDiscountAmount(),
                        vyaparInvoice.getTaxAmount(),
                        vyaparInvoice.getShippingCharge(),
                        vyaparInvoice.getTotalAmount(),
                        escapeCsv(vyaparInvoice.getPaymentMode()),
                        escapeCsv(vyaparInvoice.getPaymentStatus()),
                        escapeCsv(vyaparInvoice.getOrderSource()),
                        escapeCsv(vyaparInvoice.getOrderStatus())
                );
            }
        }

        writer.flush();
        log.info("Generated Vyapar CSV: {} orders, {} line items", exportedOrders, totalLineItems);

        return baos.toByteArray();
    }

    /**
     * Builds a complete shipping address string from customer address components.
     * Concatenates addressLine1 and addressLine2 (if present).
     * 
     * @param customer The customer whose address to build
     * @return Concatenated shipping address or empty string if no customer
     */
    private String buildShippingAddress(Customer customer) {
        if (customer == null) {
            return "";
        }

        StringBuilder address = new StringBuilder();
        
        if (customer.getAddressLine1() != null && !customer.getAddressLine1().trim().isEmpty()) {
            address.append(customer.getAddressLine1().trim());
        }
        
        if (customer.getAddressLine2() != null && !customer.getAddressLine2().trim().isEmpty()) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(customer.getAddressLine2().trim());
        }
        
        return address.toString();
    }

    // ==================== Invoice Generation (Requirement 29) ====================

    /**
     * Prepares all data required to render a GST tax invoice for an order.
     * Implements Requirement 29.1, 29.2: assemble vendor details, customer details,
     * order line items and tax breakdowns/totals into an {@link InvoiceDTO}.
     *
     * <p>This is a read-only operation that performs no database modifications.
     * Vendor/company details are sourced from {@link ConfigurationService}
     * (backed by the CompanyConfig entity). GST is split into CGST + SGST for
     * intra-state sales and IGST for inter-state sales.
     *
     * @param orderId the id of the order to build an invoice for
     * @return a fully populated {@link InvoiceDTO}
     * @throws ResourceNotFoundException if no order exists for {@code orderId}
     * @throws IllegalStateException     if the order is not yet invoiceable (NEW/CONFIRMED)
     */
    @Transactional(readOnly = true)
    public InvoiceDTO prepareInvoiceData(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }

        log.info("Preparing invoice data for order ID: {}", orderId);

        // Step 1: Load order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate: invoice can only be generated for PAID or later statuses
        if (order.getStatus() == Order.OrderStatus.NEW
                || order.getStatus() == Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot generate invoice for order in " + order.getStatus()
                            + " status. Order must be PAID or later.");
        }

        // Step 2: Vendor/company details from configuration
        CompanyConfig company = configurationService.getConfiguration();

        // Step 3: Prepare invoice items with per-line GST calculation
        List<InvoiceItemDTO> invoiceItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            InvoiceItemDTO invoiceItem = buildInvoiceItem(item);
            invoiceItems.add(invoiceItem);
            subtotal = subtotal.add(invoiceItem.getTaxableAmount());
            totalTax = totalTax.add(invoiceItem.getGstAmount());
        }

        subtotal = scale(subtotal);
        totalTax = scale(totalTax);

        // Step 4: Split tax into CGST/SGST (intra-state) or IGST (inter-state)
        BigDecimal cgstAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sgstAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal igstAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (isIntraStateSale(company, order.getCustomer())) {
            // Intra-state: CGST + SGST. Keep the two halves summing to totalTax.
            cgstAmount = totalTax.divide(TWO, 2, RoundingMode.HALF_UP);
            sgstAmount = totalTax.subtract(cgstAmount);
        } else {
            // Inter-state: IGST
            igstAmount = totalTax;
        }

        // Step 5: Payment details
        BigDecimal paidAmount = scale(calculateTotalPaid(order));
        BigDecimal totalAmount = order.getTotalAmount() != null
                ? scale(order.getTotalAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal balanceAmount = scale(totalAmount.subtract(paidAmount));

        Customer customer = order.getCustomer();

        // Step 6: Build complete invoice DTO
        InvoiceDTO invoice = InvoiceDTO.builder()
                .invoiceNumber(generateInvoiceNumber(order))
                .invoiceDate(LocalDate.now())
                .vendorName(company.getCompanyName())
                .vendorGSTIN(company.getGstin())
                .vendorAddress(company.getAddress())
                .vendorPhone(company.getPhone())
                .vendorEmail(company.getEmail())
                .customerName(customer != null ? customer.getName() : "")
                .customerGSTIN(customer != null ? customer.getGstin() : null)
                .billingAddress(buildShippingAddress(customer))
                .shippingAddress(buildShippingAddress(customer))
                .customerPhone(customer != null ? customer.getPhone() : "")
                .customerEmail(customer != null ? customer.getEmail() : "")
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .items(invoiceItems)
                .subtotal(subtotal)
                .discountAmount(order.getDiscountAmount() != null
                        ? scale(order.getDiscountAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .taxableAmount(subtotal)
                .cgstAmount(cgstAmount)
                .sgstAmount(sgstAmount)
                .igstAmount(igstAmount)
                .totalTax(totalTax)
                .shippingCharge(order.getShippingCharge() != null
                        ? scale(order.getShippingCharge()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalAmount(totalAmount)
                .paymentStatus(order.getPaymentStatus())
                .paidAmount(paidAmount)
                .balanceAmount(balanceAmount)
                .termsAndConditions(resolveTermsAndConditions(company))
                .bankDetails(company.getBankDetails())
                .build();

        log.info("Invoice data prepared: invoiceNumber={}, items={}, totalAmount={}",
                invoice.getInvoiceNumber(), invoiceItems.size(), invoice.getTotalAmount());

        return invoice;
    }

    /**
     * Builds a single invoice line item with its GST breakdown from an order item.
     * Uses the item's stored snapshot values so the invoice stays consistent with
     * the totals recorded on the order at creation time.
     */
    private InvoiceItemDTO buildInvoiceItem(OrderItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
        BigDecimal lineSubtotal = scale(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal lineDiscount = item.getDiscount() != null
                ? scale(item.getDiscount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = scale(lineSubtotal.subtract(lineDiscount));
        BigDecimal gstAmount = item.getTaxAmount() != null
                ? scale(item.getTaxAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Product product = item.getProduct();
        String hsnCode = product != null ? product.getHsnCode() : null;
        String unit = (product != null && product.getUnit() != null) ? product.getUnit() : "PCS";

        return InvoiceItemDTO.builder()
                .productName(item.getProductNameSnapshot())
                .sku(item.getSkuSnapshot())
                .hsnCode(hsnCode)
                .quantity(quantity)
                .unit(unit)
                .unitPrice(scale(unitPrice))
                .discountPercentage(calculateDiscountPercentage(lineSubtotal, lineDiscount))
                .discountAmount(lineDiscount)
                .taxableAmount(taxableAmount)
                .gstRate(resolveGstRate(product, taxableAmount, gstAmount))
                .gstAmount(gstAmount)
                .totalAmount(scale(taxableAmount.add(gstAmount)))
                .build();
    }

    /**
     * Resolves the GST rate for a line. Prefers the product's configured GST rate;
     * if absent, derives it from the recorded GST amount and taxable amount.
     */
    private BigDecimal resolveGstRate(Product product, BigDecimal taxableAmount, BigDecimal gstAmount) {
        if (product != null && product.getGstRate() != null
                && product.getGstRate().compareTo(BigDecimal.ZERO) > 0) {
            return scale(product.getGstRate());
        }
        if (taxableAmount.compareTo(BigDecimal.ZERO) > 0) {
            return gstAmount.multiply(HUNDRED).divide(taxableAmount, 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the discount percentage for a line relative to its gross amount.
     */
    private BigDecimal calculateDiscountPercentage(BigDecimal lineSubtotal, BigDecimal lineDiscount) {
        if (lineSubtotal == null || lineSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return lineDiscount.multiply(HUNDRED).divide(lineSubtotal, 2, RoundingMode.HALF_UP);
    }

    /**
     * Sums all recorded payments for an order.
     */
    private BigDecimal calculateTotalPaid(Order order) {
        if (order.getPaymentRecords() == null || order.getPaymentRecords().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return order.getPaymentRecords().stream()
                .map(PaymentRecord::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Determines whether a sale is intra-state (same GST state as the company) and
     * therefore attracts CGST + SGST rather than IGST. When the state cannot be
     * reliably determined (missing GSTINs) the sale defaults to intra-state, which
     * is the common case for a single regional business.
     *
     * <p>Comparison uses the first two characters of the GSTIN, which encode the
     * Indian state code.
     */
    private boolean isIntraStateSale(CompanyConfig company, Customer customer) {
        String companyGstin = company != null ? company.getGstin() : null;
        String customerGstin = customer != null ? customer.getGstin() : null;
        if (companyGstin != null && companyGstin.length() >= 2
                && customerGstin != null && customerGstin.length() >= 2) {
            return companyGstin.substring(0, 2).equals(customerGstin.substring(0, 2));
        }
        // Default to intra-state when state codes are not both available.
        return true;
    }

    /**
     * Generates a human-readable invoice number in the format INV-YYYYMM-XXXX.
     * The numeric suffix is derived from the order id so the value is unique and
     * deterministic without requiring a separate invoice counter table.
     */
    private String generateInvoiceNumber(Order order) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long suffix = order.getId() != null ? order.getId() : 0L;
        return String.format("INV-%s-%04d", datePart, suffix);
    }

    /**
     * Resolves invoice terms and conditions into a list of lines, falling back to
     * sensible defaults when none are configured.
     */
    private List<String> resolveTermsAndConditions(CompanyConfig company) {
        String terms = company != null ? company.getTermsAndConditions() : null;
        if (terms != null && !terms.trim().isEmpty()) {
            return Arrays.stream(terms.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of(
                "Goods once sold will not be taken back or exchanged.",
                "All disputes are subject to local jurisdiction.",
                "This is a computer-generated invoice."
        );
    }

    /**
     * Scales a monetary value to 2 decimal places using HALF_UP rounding.
     */
    private BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Invoice PDF generation (Requirement 29.1, 29.2, 29.3, 29.4)
    // ------------------------------------------------------------------

    private static final DateTimeFormatter INVOICE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color HEADER_BG = new Color(33, 102, 70); // ayurveda green
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color SUMMARY_BG = new Color(235, 240, 236);

    /**
     * Generates a GST tax invoice PDF for an order. This is a convenience method
     * that prepares the invoice data via {@link #prepareInvoiceData(Long)} and
     * renders it to a PDF byte array.
     *
     * <p>The generated PDF contains the vendor header, invoice number and date,
     * customer billing/shipping details, a line item table (HSN, quantity, rate,
     * taxable value, GST and line total), a tax summary (CGST/SGST/IGST), the grand
     * total, payment status and the terms and conditions.
     *
     * <p>Supports Requirement 29: Invoice Generation.
     *
     * @param orderId the id of the order to build an invoice for
     * @return the rendered invoice as a PDF byte array
     * @throws ResourceNotFoundException if no order exists for {@code orderId}
     * @throws IllegalStateException     if the order is not yet invoiceable (NEW/CONFIRMED)
     */
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long orderId) {
        InvoiceDTO invoice = prepareInvoiceData(orderId);
        return renderInvoicePdf(invoice);
    }

    /**
     * Renders a populated {@link InvoiceDTO} into a PDF byte array using OpenPDF.
     *
     * @param invoice the invoice data to render (must not be {@code null})
     * @return the rendered invoice as a PDF byte array
     */
    public byte[] renderInvoicePdf(InvoiceDTO invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice data cannot be null");
        }

        log.info("Rendering invoice PDF for invoiceNumber={}", invoice.getInvoiceNumber());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, HEADER_FG);
            Font vendorFont = new Font(Font.HELVETICA, 10, Font.NORMAL, HEADER_FG);
            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            Font tableHeaderFont = new Font(Font.HELVETICA, 9, Font.BOLD, HEADER_FG);
            Font footerFont = new Font(Font.HELVETICA, 8, Font.ITALIC);

            addVendorHeader(document, invoice, titleFont, vendorFont);
            addInvoiceMeta(document, invoice, labelFont, normalFont);
            addPartyDetails(document, invoice, sectionFont, labelFont, normalFont);
            addLineItemTable(document, invoice, tableHeaderFont, normalFont);
            addTaxSummary(document, invoice, labelFont, normalFont);
            addPaymentStatus(document, invoice, sectionFont, labelFont, normalFont);
            addTermsAndConditions(document, invoice, sectionFont, normalFont);
            addFooter(document, footerFont);

            document.close();

            byte[] pdfBytes = outputStream.toByteArray();
            log.info("Invoice PDF generated successfully, size: {} bytes", pdfBytes.length);
            return pdfBytes;
        } catch (DocumentException | IOException e) {
            log.error("Error generating invoice PDF for invoiceNumber {}: {}",
                    invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    private void addVendorHeader(Document document, InvoiceDTO invoice, Font titleFont, Font vendorFont)
            throws DocumentException {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(12);

        Paragraph vendorName = new Paragraph(safe(invoice.getVendorName(), "TAX INVOICE"), titleFont);
        cell.addElement(vendorName);

        if (invoice.getVendorAddress() != null && !invoice.getVendorAddress().isBlank()) {
            cell.addElement(new Paragraph(invoice.getVendorAddress(), vendorFont));
        }
        StringBuilder contact = new StringBuilder();
        if (invoice.getVendorPhone() != null && !invoice.getVendorPhone().isBlank()) {
            contact.append("Phone: ").append(invoice.getVendorPhone());
        }
        if (invoice.getVendorEmail() != null && !invoice.getVendorEmail().isBlank()) {
            if (contact.length() > 0) contact.append("   ");
            contact.append("Email: ").append(invoice.getVendorEmail());
        }
        if (contact.length() > 0) {
            cell.addElement(new Paragraph(contact.toString(), vendorFont));
        }
        if (invoice.getVendorGSTIN() != null && !invoice.getVendorGSTIN().isBlank()) {
            cell.addElement(new Paragraph("GSTIN: " + invoice.getVendorGSTIN(), vendorFont));
        }

        header.addCell(cell);
        header.setSpacingAfter(8);
        document.add(header);

        Font invoiceLabelFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Paragraph invoiceLabel = new Paragraph("TAX INVOICE", invoiceLabelFont);
        invoiceLabel.setAlignment(Element.ALIGN_CENTER);
        invoiceLabel.setSpacingAfter(8);
        document.add(invoiceLabel);
    }

    private void addInvoiceMeta(Document document, InvoiceDTO invoice, Font labelFont, Font normalFont)
            throws DocumentException {
        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setWidths(new int[]{2, 3, 2, 3});

        meta.addCell(metaCell("Invoice No:", labelFont));
        meta.addCell(metaCell(safe(invoice.getInvoiceNumber(), "-"), normalFont));
        meta.addCell(metaCell("Invoice Date:", labelFont));
        meta.addCell(metaCell(formatDate(invoice.getInvoiceDate()), normalFont));

        meta.addCell(metaCell("Order No:", labelFont));
        meta.addCell(metaCell(safe(invoice.getOrderNumber(), "-"), normalFont));
        meta.addCell(metaCell("Order Date:", labelFont));
        meta.addCell(metaCell(formatDate(invoice.getOrderDate()), normalFont));

        meta.setSpacingAfter(10);
        document.add(meta);
    }

    private void addPartyDetails(Document document, InvoiceDTO invoice, Font sectionFont,
                                 Font labelFont, Font normalFont) throws DocumentException {
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new int[]{1, 1});

        // Billing details
        PdfPCell billing = new PdfPCell();
        billing.setPadding(8);
        billing.addElement(new Paragraph("Bill To", sectionFont));
        billing.addElement(new Paragraph(safe(invoice.getCustomerName(), "-"), labelFont));
        if (invoice.getBillingAddress() != null && !invoice.getBillingAddress().isBlank()) {
            billing.addElement(new Paragraph(invoice.getBillingAddress(), normalFont));
        }
        if (invoice.getCustomerPhone() != null && !invoice.getCustomerPhone().isBlank()) {
            billing.addElement(new Paragraph("Phone: " + invoice.getCustomerPhone(), normalFont));
        }
        if (invoice.getCustomerEmail() != null && !invoice.getCustomerEmail().isBlank()) {
            billing.addElement(new Paragraph("Email: " + invoice.getCustomerEmail(), normalFont));
        }
        if (invoice.getCustomerGSTIN() != null && !invoice.getCustomerGSTIN().isBlank()) {
            billing.addElement(new Paragraph("GSTIN: " + invoice.getCustomerGSTIN(), normalFont));
        }
        parties.addCell(billing);

        // Shipping details
        PdfPCell shipping = new PdfPCell();
        shipping.setPadding(8);
        shipping.addElement(new Paragraph("Ship To", sectionFont));
        shipping.addElement(new Paragraph(safe(invoice.getCustomerName(), "-"), labelFont));
        if (invoice.getShippingAddress() != null && !invoice.getShippingAddress().isBlank()) {
            shipping.addElement(new Paragraph(invoice.getShippingAddress(), normalFont));
        }
        parties.addCell(shipping);

        parties.setSpacingAfter(10);
        document.add(parties);
    }

    private void addLineItemTable(Document document, InvoiceDTO invoice, Font headerFont, Font normalFont)
            throws DocumentException {
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{4, 2, 1, 2, 3, 2, 2, 3});

        addHeaderCell(table, "Item", headerFont);
        addHeaderCell(table, "HSN", headerFont);
        addHeaderCell(table, "Qty", headerFont);
        addHeaderCell(table, "Rate", headerFont);
        addHeaderCell(table, "Taxable", headerFont);
        addHeaderCell(table, "GST%", headerFont);
        addHeaderCell(table, "GST Amt", headerFont);
        addHeaderCell(table, "Total", headerFont);

        if (invoice.getItems() != null) {
            for (InvoiceItemDTO item : invoice.getItems()) {
                table.addCell(bodyCell(safe(item.getProductName(), "-"), normalFont, Element.ALIGN_LEFT));
                table.addCell(bodyCell(safe(item.getHsnCode(), "-"), normalFont, Element.ALIGN_CENTER));
                table.addCell(bodyCell(item.getQuantity() != null
                        ? String.valueOf(item.getQuantity()) : "0", normalFont, Element.ALIGN_CENTER));
                table.addCell(bodyCell(money(item.getUnitPrice()), normalFont, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(item.getTaxableAmount()), normalFont, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(percent(item.getGstRate()), normalFont, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(item.getGstAmount()), normalFont, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(item.getTotalAmount()), normalFont, Element.ALIGN_RIGHT));
            }
        }

        table.setSpacingAfter(8);
        document.add(table);
    }

    private void addTaxSummary(Document document, InvoiceDTO invoice, Font labelFont, Font normalFont)
            throws DocumentException {
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(45);
        summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
        summary.setWidths(new int[]{3, 2});

        addSummaryRow(summary, "Subtotal", money(invoice.getSubtotal()), labelFont, normalFont, false);
        if (invoice.getDiscountAmount() != null
                && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            addSummaryRow(summary, "Discount", "-" + money(invoice.getDiscountAmount()),
                    labelFont, normalFont, false);
        }
        addSummaryRow(summary, "Taxable Amount", money(invoice.getTaxableAmount()),
                labelFont, normalFont, false);

        // Show CGST/SGST for intra-state, IGST for inter-state
        boolean hasIgst = invoice.getIgstAmount() != null
                && invoice.getIgstAmount().compareTo(BigDecimal.ZERO) > 0;
        if (hasIgst) {
            addSummaryRow(summary, "IGST", money(invoice.getIgstAmount()), labelFont, normalFont, false);
        } else {
            addSummaryRow(summary, "CGST", money(invoice.getCgstAmount()), labelFont, normalFont, false);
            addSummaryRow(summary, "SGST", money(invoice.getSgstAmount()), labelFont, normalFont, false);
        }
        addSummaryRow(summary, "Total Tax", money(invoice.getTotalTax()), labelFont, normalFont, false);

        if (invoice.getShippingCharge() != null
                && invoice.getShippingCharge().compareTo(BigDecimal.ZERO) > 0) {
            addSummaryRow(summary, "Shipping", money(invoice.getShippingCharge()),
                    labelFont, normalFont, false);
        }
        addSummaryRow(summary, "Grand Total", money(invoice.getTotalAmount()),
                labelFont, normalFont, true);

        summary.setSpacingAfter(10);
        document.add(summary);
    }

    private void addPaymentStatus(Document document, InvoiceDTO invoice, Font sectionFont,
                                  Font labelFont, Font normalFont) throws DocumentException {
        PdfPTable payment = new PdfPTable(4);
        payment.setWidthPercentage(100);
        payment.setWidths(new int[]{2, 2, 2, 2});

        payment.addCell(metaCell("Payment Status:", labelFont));
        payment.addCell(metaCell(invoice.getPaymentStatus() != null
                ? invoice.getPaymentStatus().name() : "-", normalFont));
        payment.addCell(metaCell("Paid Amount:", labelFont));
        payment.addCell(metaCell(money(invoice.getPaidAmount()), normalFont));

        payment.addCell(metaCell("Balance Due:", labelFont));
        payment.addCell(metaCell(money(invoice.getBalanceAmount()), normalFont));
        payment.addCell(metaCell("", normalFont));
        payment.addCell(metaCell("", normalFont));

        payment.setSpacingAfter(10);
        document.add(payment);

        if (invoice.getBankDetails() != null && !invoice.getBankDetails().isBlank()) {
            Paragraph bank = new Paragraph("Bank Details", sectionFont);
            bank.setSpacingAfter(2);
            document.add(bank);
            document.add(new Paragraph(invoice.getBankDetails(), normalFont));
        }
    }

    private void addTermsAndConditions(Document document, InvoiceDTO invoice, Font sectionFont, Font normalFont)
            throws DocumentException {
        List<String> terms = invoice.getTermsAndConditions();
        if (terms == null || terms.isEmpty()) {
            return;
        }
        Paragraph heading = new Paragraph("Terms & Conditions", sectionFont);
        heading.setSpacingBefore(8);
        heading.setSpacingAfter(2);
        document.add(heading);
        int index = 1;
        for (String term : terms) {
            document.add(new Paragraph(index++ + ". " + term, normalFont));
        }
    }

    private void addFooter(Document document, Font footerFont) throws DocumentException {
        Paragraph footer = new Paragraph(
                "This is a computer-generated invoice. Generated on "
                        + LocalDate.now().format(INVOICE_DATE_FORMAT), footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(16);
        document.add(footer);
    }

    private PdfPCell metaCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(3);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private PdfPCell bodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        return cell;
    }

    private void addSummaryRow(PdfPTable table, String label, String value,
                               Font labelFont, Font normalFont, boolean highlight) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPadding(4);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, highlight ? labelFont : normalFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(4);
        if (highlight) {
            labelCell.setBackgroundColor(SUMMARY_BG);
            valueCell.setBackgroundColor(SUMMARY_BG);
        }
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(INVOICE_DATE_FORMAT) : "-";
    }

    private String money(BigDecimal value) {
        BigDecimal amount = value != null ? value : BigDecimal.ZERO;
        return String.format("%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
    }

    private String percent(BigDecimal value) {
        if (value == null) {
            return "0%";
        }
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
