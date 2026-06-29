package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.DispatchLabelDTO;
import com.ayurveda.platform.dto.response.LabelProductLineDTO;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.DispatchLabel;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.repository.DispatchLabelRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.util.BarcodeGenerator;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DispatchService {

    private final OrderRepository orderRepository;
    private final DispatchLabelRepository dispatchLabelRepository;
    private final ConfigurationService configurationService;

    /**
     * Returns orders that are PAID or PACKED — ready for dispatch.
     */
    public Page<Order> getDispatchQueue(Pageable pageable) {
        return orderRepository.findAllByStatusIn(
            List.of(Order.OrderStatus.PAID, Order.OrderStatus.PACKED),
            pageable
        );
    }

    /**
     * Generate dispatch labels for a batch of orders.
     * Marks orders as PACKED if not already.
     */
    public List<DispatchLabel> generateLabels(List<Long> orderIds, String courierPartner) {
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<DispatchLabel> labels = new ArrayList<>();

        for (Long orderId : orderIds) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

            // Validate order status
            if (order.getStatus() != Order.OrderStatus.PAID && 
                order.getStatus() != Order.OrderStatus.PACKED) {
                log.warn("Order {} is in status {} and cannot be dispatched", orderId, order.getStatus());
                continue;
            }

            // Update order status to PACKED if still PAID
            if (order.getStatus() == Order.OrderStatus.PAID) {
                order.setStatus(Order.OrderStatus.PACKED);
                orderRepository.save(order);
            }

            DispatchLabel label = new DispatchLabel();
            label.setOrder(order);
            label.setBatchId(batchId);
            label.setCourierPartner(courierPartner != null ? courierPartner : "Default");
            label.setTrackingNumber(generateTrackingNumber());
            label.setStatus(DispatchLabel.LabelStatus.GENERATED);
            label.setGeneratedAt(LocalDateTime.now());
            label.setWeightGrams(BigDecimal.valueOf(500));

            labels.add(dispatchLabelRepository.save(label));
            log.info("Generated dispatch label for order {}: {}", orderId, label.getTrackingNumber());
        }

        return labels;
    }

    /**
     * Mark orders as DISPATCHED with tracking info.
     */
    public void markDispatched(List<Long> orderIds) {
        for (Long orderId : orderIds) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
            order.setStatus(Order.OrderStatus.DISPATCHED);
            order.setDispatchedAt(LocalDateTime.now());
            orderRepository.save(order);

            // Update label status
            dispatchLabelRepository.findByOrder_Id(orderId).ifPresent(label -> {
                label.setStatus(DispatchLabel.LabelStatus.SHIPPED);
                dispatchLabelRepository.save(label);
            });
            
            log.info("Marked order {} as dispatched", orderId);
        }
    }

    private String generateTrackingNumber() {
        // Use UUID instead of timestamp for better uniqueness under high load
        return "TRK-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase().replace("-", "");
    }
    
    /**
     * Check if order status allows dispatch label generation.
     * Order must be in PAID, PACKED, DISPATCHED, or DELIVERED status.
     * 
     * Implements Requirement 12.1: Require order status to be PAID or later
     * 
     * @param status The order status to check
     * @return true if order can have a dispatch label generated, false otherwise
     */
    private boolean isOrderReadyForLabel(Order.OrderStatus status) {
        return status == Order.OrderStatus.PAID ||
               status == Order.OrderStatus.PACKED ||
               status == Order.OrderStatus.DISPATCHED ||
               status == Order.OrderStatus.DELIVERED;
    }

    /**
     * Generates a Code128 barcode image for the given order number.
     * This method is used for dispatch label generation.
     *
     * @param orderNumber the order number to encode
     * @return BufferedImage containing the barcode
     * @throws IllegalArgumentException if orderNumber is null or empty
     */
    public BufferedImage generateOrderBarcode(String orderNumber) {
        log.info("Generating barcode for order number: {}", orderNumber);
        return BarcodeGenerator.generateCode128Barcode(orderNumber);
    }

    /**
     * Generates a Code128 barcode image with custom dimensions.
     *
     * @param orderNumber the order number to encode
     * @param width the width of the barcode image
     * @param height the height of the barcode image
     * @return BufferedImage containing the barcode
     * @throws IllegalArgumentException if orderNumber is null or empty, or if dimensions are invalid
     */
    public BufferedImage generateOrderBarcode(String orderNumber, int width, int height) {
        log.info("Generating barcode for order number: {} with dimensions {}x{}", orderNumber, width, height);
        return BarcodeGenerator.generateCode128Barcode(orderNumber, width, height);
    }

    /**
     * Prepare dispatch label data for a single order.
     * Extracts all information needed for PDF label generation.
     * 
     * Implements Requirements:
     * - 12.1: Label contains order number, date, customer details
     * - 12.2: Extract order details and customer shipping information
     * - 12.3: Extract product list with quantities
     * - 12.5: Include vendor information from configuration
     * 
     * @param orderId The order ID to prepare label data for
     * @return DispatchLabelDTO containing all label information
     * @throws ResourceNotFoundException if order not found
     */
    public DispatchLabelDTO prepareLabelData(Long orderId) {
        log.info("Preparing dispatch label data for order ID: {}", orderId);
        
        // Fetch order with all relationships
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        
        // Validate order status is PAID or later (Requirement 12.1)
        if (!isOrderReadyForLabel(order.getStatus())) {
            throw new IllegalStateException(
                "Order " + order.getOrderNumber() + " cannot have a dispatch label generated. " +
                "Order status must be PAID or later. Current status: " + order.getStatus()
            );
        }
        
        Customer customer = order.getCustomer();
        if (customer == null) {
            throw new ResourceNotFoundException("Customer", "order.customerId", orderId);
        }
        
        // Extract order details (Requirement 12.2)
        String orderNumber = order.getOrderNumber();
        var orderDate = order.getOrderDate();
        
        // Extract customer shipping information (Requirement 12.2)
        String customerName = customer.getName();
        String shippingAddress = buildShippingAddress(customer);
        String city = customer.getCity() != null ? customer.getCity() : "";
        String state = customer.getState() != null ? customer.getState() : "";
        String pincode = customer.getPincode() != null ? customer.getPincode() : "";
        String phone = customer.getPhone() != null ? customer.getPhone() : "";
        
        // Extract product list with quantities (Requirement 12.3)
        List<LabelProductLineDTO> products = extractProductLines(order.getItems());
        
        // Calculate total items and weight
        Integer totalItems = calculateTotalItems(order.getItems());
        BigDecimal totalWeight = calculateTotalWeight(order.getItems());
        
        // Generate barcode string for order number (Requirement 12.4)
        String barcode = generateBarcodeString(orderNumber);
        
        // Include vendor information from configuration (Requirement 12.5)
        String vendorName = configurationService.getCompanyName();
        String vendorAddress = configurationService.getAddress();
        String vendorPhone = configurationService.getPhone();
        
        // Build and return DTO
        DispatchLabelDTO labelData = DispatchLabelDTO.builder()
                .orderNumber(orderNumber)
                .orderDate(orderDate)
                .customerName(customerName)
                .shippingAddress(shippingAddress)
                .city(city)
                .state(state)
                .pincode(pincode)
                .phone(phone)
                .products(products)
                .totalItems(totalItems)
                .totalWeight(totalWeight)
                .orderAmount(order.getTotalAmount())
                .paymentMode(order.getPaymentMode())
                .barcode(barcode)
                .vendorName(vendorName)
                .vendorAddress(vendorAddress)
                .vendorPhone(vendorPhone)
                .build();
        
        log.info("Successfully prepared dispatch label data for order {}", orderNumber);
        return labelData;
    }
    
    /**
     * Build complete shipping address from customer address fields.
     * Combines addressLine1, addressLine2 (if present).
     */
    private String buildShippingAddress(Customer customer) {
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
    
    /**
     * Extract product lines from order items.
     * Maps OrderItem entities to LabelProductLineDTO for label display.
     */
    private List<LabelProductLineDTO> extractProductLines(List<OrderItem> items) {
        return items.stream()
                .map(item -> {
                    // Get weight from product if available
                    BigDecimal weight = BigDecimal.ZERO;
                    if (item.getProduct() != null && item.getProduct().getWeightGrams() != null) {
                        weight = item.getProduct().getWeightGrams();
                    }
                    
                    return LabelProductLineDTO.builder()
                            .productName(item.getProductNameSnapshot())
                            .sku(item.getSkuSnapshot() != null ? item.getSkuSnapshot() : "")
                            .quantity(item.getQuantity())
                            .weight(weight)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Calculate total number of items across all order items.
     */
    private Integer calculateTotalItems(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getQuantity)
                .reduce(0, Integer::sum);
    }
    
    /**
     * Calculate total weight of all items in the order.
     * Total weight = sum of (quantity × weight) for each item.
     */
    private BigDecimal calculateTotalWeight(List<OrderItem> items) {
        return items.stream()
                .map(item -> {
                    BigDecimal weight = BigDecimal.ZERO;
                    if (item.getProduct() != null && item.getProduct().getWeightGrams() != null) {
                        weight = item.getProduct().getWeightGrams();
                    }
                    return weight.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Generate barcode string for order number.
     * Returns the order number as-is, ready for barcode encoding.
     * The actual barcode image generation will be handled by the PDF generation service.
     */
    private String generateBarcodeString(String orderNumber) {
        return orderNumber;
    }
    
    /**
     * Generate a single dispatch label PDF for an order.
     * Creates an A4-sized PDF with order details, customer information, product list, and barcode.
     * 
     * Implements Requirements:
     * - 12.1: Label contains order number, date, customer details
     * - 12.2: Label includes customer shipping information
     * - 12.3: Label includes product list with quantities
     * - 12.4: Label includes Code128 barcode encoding order number
     * - 12.5: Label includes vendor information
     * - 12.7: PDF size is optimized (<500KB per label)
     * 
     * @param orderId The order ID to generate label for
     * @return byte array containing the PDF document
     * @throws ResourceNotFoundException if order not found
     */
    public byte[] generateSingleLabel(Long orderId) {
        log.info("Generating PDF dispatch label for order ID: {}", orderId);
        
        // Prepare label data using existing method (Requirements 12.1, 12.2, 12.3, 12.5)
        DispatchLabelDTO labelData = prepareLabelData(orderId);
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Create A4 document with margins
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, outputStream);
            document.open();
            
            // Add document content
            addLabelHeader(document, labelData);
            addCustomerSection(document, labelData);
            addProductTable(document, labelData);
            addBarcodeSection(document, labelData);
            addVendorSection(document, labelData);
            
            document.close();
            
            byte[] pdfBytes = outputStream.toByteArray();
            log.info("Successfully generated PDF label for order {}. Size: {} bytes", 
                    labelData.getOrderNumber(), pdfBytes.length);
            
            return pdfBytes;
            
        } catch (DocumentException | IOException e) {
            log.error("Error generating PDF label for order ID {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF label for order " + orderId, e);
        }
    }
    
    /**
     * Prepare dispatch label data for multiple orders.
     * Maps each order ID to its corresponding DispatchLabelDTO.
     * 
     * @param orderIds List of order IDs to prepare label data for
     * @return List of DispatchLabelDTO containing label information for all orders
     * @throws ResourceNotFoundException if any order is not found
     */
    public List<DispatchLabelDTO> prepareBulkLabelData(List<Long> orderIds) {
        log.info("Preparing dispatch label data for {} orders", orderIds.size());
        
        return orderIds.stream()
                .map(this::prepareLabelData)
                .collect(Collectors.toList());
    }
    
    /**
     * Generate bulk PDF dispatch labels for multiple orders.
     * Creates a single PDF document containing one label page per order.
     * 
     * Implements Requirements:
     * - 12.6: Generate bulk labels creating a single PDF containing multiple labels
     * - 12.7: Generated PDF should be less than 500KB per label
     * 
     * @param orderIds List of order IDs to generate labels for
     * @return byte array containing the PDF document with all labels
     * @throws ResourceNotFoundException if any order is not found
     * @throws IllegalArgumentException if orderIds is null or empty
     */
    public byte[] generateBulkLabels(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("Order IDs list cannot be null or empty");
        }
        
        log.info("Generating bulk dispatch labels for {} orders", orderIds.size());
        
        // Prepare label data for all orders
        List<DispatchLabelDTO> labels = prepareBulkLabelData(orderIds);
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Create A4 document with margins
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, outputStream);
            document.open();
            
            // Add each label as a separate page
            for (int i = 0; i < labels.size(); i++) {
                DispatchLabelDTO labelData = labels.get(i);
                
                // Add page break before each label (except the first one)
                if (i > 0) {
                    document.newPage();
                }
                
                // Add label content for this order
                addLabelHeader(document, labelData);
                addCustomerSection(document, labelData);
                addProductTable(document, labelData);
                addBarcodeSection(document, labelData);
                addVendorSection(document, labelData);
                
                log.debug("Added label {} of {} to bulk PDF for order {}", 
                        i + 1, labels.size(), labelData.getOrderNumber());
            }
            
            document.close();
            
            byte[] pdfBytes = outputStream.toByteArray();
            log.info("Successfully generated bulk PDF with {} labels. Total size: {} bytes ({} KB)", 
                    labels.size(), pdfBytes.length, pdfBytes.length / 1024);
            
            return pdfBytes;
            
        } catch (DocumentException | IOException e) {
            log.error("Error generating bulk PDF labels: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate bulk dispatch labels PDF", e);
        }
    }
    
    /**
     * Add header section with order number and date.
     */
    private void addLabelHeader(Document document, DispatchLabelDTO labelData) throws DocumentException {
        // Title
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("DISPATCH LABEL", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);
        
        // Order info
        Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        
        Paragraph orderInfo = new Paragraph();
        orderInfo.add(new Chunk("Order Number: ", boldFont));
        orderInfo.add(new Chunk(labelData.getOrderNumber(), normalFont));
        orderInfo.setSpacingAfter(5);
        document.add(orderInfo);
        
        Paragraph dateInfo = new Paragraph();
        dateInfo.add(new Chunk("Order Date: ", boldFont));
        dateInfo.add(new Chunk(labelData.getOrderDate().toString(), normalFont));
        dateInfo.setSpacingAfter(15);
        document.add(dateInfo);
        
        // Separator line
        document.add(new Paragraph("________________________________________"));
        document.add(Chunk.NEWLINE);
    }
    
    /**
     * Add customer shipping information section.
     */
    private void addCustomerSection(Document document, DispatchLabelDTO labelData) throws DocumentException {
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        
        Paragraph section = new Paragraph("SHIP TO:", sectionFont);
        section.setSpacingAfter(10);
        document.add(section);
        
        // Customer name
        Paragraph name = new Paragraph(labelData.getCustomerName(), boldFont);
        name.setSpacingAfter(5);
        document.add(name);
        
        // Address
        if (labelData.getShippingAddress() != null && !labelData.getShippingAddress().isEmpty()) {
            Paragraph address = new Paragraph(labelData.getShippingAddress(), normalFont);
            address.setSpacingAfter(3);
            document.add(address);
        }
        
        // City, State, Pincode
        StringBuilder locationLine = new StringBuilder();
        if (labelData.getCity() != null && !labelData.getCity().isEmpty()) {
            locationLine.append(labelData.getCity());
        }
        if (labelData.getState() != null && !labelData.getState().isEmpty()) {
            if (locationLine.length() > 0) {
                locationLine.append(", ");
            }
            locationLine.append(labelData.getState());
        }
        if (labelData.getPincode() != null && !labelData.getPincode().isEmpty()) {
            if (locationLine.length() > 0) {
                locationLine.append(" - ");
            }
            locationLine.append(labelData.getPincode());
        }
        
        if (locationLine.length() > 0) {
            Paragraph location = new Paragraph(locationLine.toString(), normalFont);
            location.setSpacingAfter(3);
            document.add(location);
        }
        
        // Phone
        if (labelData.getPhone() != null && !labelData.getPhone().isEmpty()) {
            Paragraph phone = new Paragraph();
            phone.add(new Chunk("Phone: ", boldFont));
            phone.add(new Chunk(labelData.getPhone(), normalFont));
            phone.setSpacingAfter(3);
            document.add(phone);
        }
        
        document.add(Chunk.NEWLINE);
    }
    
    /**
     * Add product table with item details.
     */
    private void addProductTable(Document document, DispatchLabelDTO labelData) throws DocumentException {
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD);
        Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        
        Paragraph section = new Paragraph("PRODUCTS:", sectionFont);
        section.setSpacingAfter(10);
        document.add(section);
        
        // Create table with 4 columns: Product, SKU, Qty, Weight
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 1.5f, 1f, 1.5f});
        
        // Header row
        addTableHeaderCell(table, "Product Name", headerFont);
        addTableHeaderCell(table, "SKU", headerFont);
        addTableHeaderCell(table, "Qty", headerFont);
        addTableHeaderCell(table, "Weight (g)", headerFont);
        
        // Data rows
        for (LabelProductLineDTO product : labelData.getProducts()) {
            addTableCell(table, product.getProductName(), cellFont);
            addTableCell(table, product.getSku() != null ? product.getSku() : "", cellFont);
            addTableCell(table, String.valueOf(product.getQuantity()), cellFont);
            
            String weight = product.getWeight() != null ? 
                    product.getWeight().setScale(0, java.math.RoundingMode.HALF_UP).toString() : "0";
            addTableCell(table, weight, cellFont);
        }
        
        document.add(table);
        
        // Summary
        Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Paragraph summary = new Paragraph();
        summary.setSpacingBefore(10);
        summary.add(new Chunk("Total Items: ", boldFont));
        summary.add(new Chunk(String.valueOf(labelData.getTotalItems()), boldFont));
        summary.add(new Chunk("    |    ", normalFont));
        summary.add(new Chunk("Total Weight: ", boldFont));
        summary.add(new Chunk(labelData.getTotalWeight().setScale(0, java.math.RoundingMode.HALF_UP) + " g", boldFont));
        summary.setSpacingAfter(15);
        document.add(summary);
    }
    
    /**
     * Add barcode section with order number barcode.
     */
    private void addBarcodeSection(Document document, DispatchLabelDTO labelData) throws DocumentException, IOException {
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        
        Paragraph section = new Paragraph("ORDER BARCODE:", sectionFont);
        section.setSpacingAfter(10);
        document.add(section);
        
        // Generate barcode image (Requirement 12.4)
        BufferedImage barcodeImage = generateOrderBarcode(labelData.getOrderNumber(), 300, 80);
        
        // Convert BufferedImage to Image for PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(barcodeImage, "PNG", baos);
        Image pdfImage = Image.getInstance(baos.toByteArray());
        pdfImage.scaleToFit(250, 70);
        pdfImage.setAlignment(Element.ALIGN_CENTER);
        
        document.add(pdfImage);
        
        // Order number text below barcode
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Paragraph barcodeText = new Paragraph(labelData.getOrderNumber(), normalFont);
        barcodeText.setAlignment(Element.ALIGN_CENTER);
        barcodeText.setSpacingAfter(15);
        document.add(barcodeText);
    }
    
    /**
     * Add vendor/sender information section.
     */
    private void addVendorSection(Document document, DispatchLabelDTO labelData) throws DocumentException {
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        Font boldFont = new Font(Font.HELVETICA, 9, Font.BOLD);
        
        // Separator line
        document.add(new Paragraph("________________________________________"));
        document.add(Chunk.NEWLINE);
        
        Paragraph section = new Paragraph("FROM:", sectionFont);
        section.setSpacingAfter(8);
        document.add(section);
        
        // Vendor name
        Paragraph vendorName = new Paragraph(labelData.getVendorName(), boldFont);
        vendorName.setSpacingAfter(3);
        document.add(vendorName);
        
        // Vendor address
        if (labelData.getVendorAddress() != null && !labelData.getVendorAddress().isEmpty()) {
            Paragraph vendorAddress = new Paragraph(labelData.getVendorAddress(), normalFont);
            vendorAddress.setSpacingAfter(3);
            document.add(vendorAddress);
        }
        
        // Vendor phone
        if (labelData.getVendorPhone() != null && !labelData.getVendorPhone().isEmpty()) {
            Paragraph vendorPhone = new Paragraph();
            vendorPhone.add(new Chunk("Phone: ", boldFont));
            vendorPhone.add(new Chunk(labelData.getVendorPhone(), normalFont));
            document.add(vendorPhone);
        }
    }
    
    /**
     * Helper method to add header cell to table.
     */
    private void addTableHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new java.awt.Color(220, 220, 220));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
    
    /**
     * Helper method to add regular cell to table.
     */
    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        table.addCell(cell);
    }
}
