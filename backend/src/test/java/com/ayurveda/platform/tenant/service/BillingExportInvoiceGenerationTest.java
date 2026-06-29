package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.InvoiceDTO;
import com.ayurveda.platform.dto.response.InvoiceItemDTO;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.entity.CompanyConfig;
import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.PaymentRecord;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.BillingExportRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for invoice generation in {@link BillingExportService}.
 *
 * <p>Covers Requirement 29: Invoice Generation
 * <ul>
 *   <li>prepareInvoiceData: totals, GST split (intra vs inter-state), paid/balance,
 *       line item mapping, terms fallback</li>
 *   <li>exception cases: null order id, missing order, NEW/CONFIRMED status</li>
 *   <li>renderInvoicePdf / generateInvoicePdf: produce non-empty PDF bytes
 *       beginning with the {@code %PDF} magic header</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Export - Invoice Generation Tests")
class BillingExportInvoiceGenerationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BillingExportRepository billingExportRepository;

    @Mock
    private ConfigurationService configurationService;

    @InjectMocks
    private BillingExportService billingExportService;

    private Customer customer;
    private CompanyConfig companyConfig;
    private Order order;

    @BeforeEach
    void setUp() {
        // Company in Maharashtra (GST state code 27)
        companyConfig = CompanyConfig.builder()
                .id(1L)
                .companyName("Ayurveda Wellness Pvt Ltd")
                .address("456 Industrial Area, Mumbai - 400058")
                .phone("080-12345678")
                .email("contact@ayurveda.example")
                .gstin("27AABCU9603R1ZM")
                .bankDetails("Bank: HDFC, A/C: 1234567890, IFSC: HDFC0001234")
                .termsAndConditions(null)
                .build();

        // Customer in Maharashtra (intra-state) by default
        customer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .addressLine1("123 Main Street")
                .addressLine2("Apartment 4B")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .gstin("27AABCU9603R1ZM")
                .createdAt(LocalDateTime.now())
                .build();

        order = buildOrder(Order.OrderStatus.PAID);
    }

    /**
     * Builds a deterministic order with two line items.
     * <p>
     * Item 1: unitPrice 300 x 2 = 600 gross, discount 20 -> taxable 580, GST 52.20
     * Item 2: unitPrice 400 x 1 = 400 gross, discount 30 -> taxable 370, GST 33.30
     * <p>
     * subtotal = 950.00, totalTax = 85.50
     */
    private Order buildOrder(Order.OrderStatus status) {
        Product product1 = Product.builder()
                .id(10L)
                .sku("ASH-001")
                .name("Ashwagandha Capsules")
                .mrp(new BigDecimal("350.00"))
                .salePrice(new BigDecimal("300.00"))
                .unit("BTL")
                .hsnCode("30049011")
                .gstRate(new BigDecimal("18.00"))
                .build();

        Order o = Order.builder()
                .id(101L)
                .orderNumber("ORD-20240115-0001")
                .customer(customer)
                .salespersonId(1L)
                .orderSource(Order.OrderSource.MANUAL)
                .status(status)
                .subtotal(new BigDecimal("950.00"))
                .discountAmount(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("85.50"))
                .shippingCharge(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("1085.50"))
                .paymentMode(Order.PaymentMode.UPI)
                .paymentStatus(Order.PaymentStatus.PARTIAL)
                .orderDate(LocalDate.of(2024, 1, 15))
                .items(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .order(o)
                .product(product1)
                .productNameSnapshot("Ashwagandha Capsules")
                .skuSnapshot("ASH-001")
                .quantity(2)
                .unitPrice(new BigDecimal("300.00"))
                .mrpSnapshot(new BigDecimal("350.00"))
                .discount(new BigDecimal("20.00"))
                .taxAmount(new BigDecimal("52.20"))
                .lineTotal(new BigDecimal("632.20"))
                .build();

        // Item 2 has no product association, so its GST rate is derived from the amounts.
        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .order(o)
                .product(null)
                .productNameSnapshot("Triphala Powder")
                .skuSnapshot("TRI-002")
                .quantity(1)
                .unitPrice(new BigDecimal("400.00"))
                .mrpSnapshot(new BigDecimal("450.00"))
                .discount(new BigDecimal("30.00"))
                .taxAmount(new BigDecimal("33.30"))
                .lineTotal(new BigDecimal("403.30"))
                .build();

        o.getItems().addAll(Arrays.asList(item1, item2));

        // Partial payment of 1000.00 against a total of 1085.50 -> balance 85.50
        PaymentRecord payment = PaymentRecord.builder()
                .id(1L)
                .order(o)
                .amount(new BigDecimal("1000.00"))
                .paymentMode(Order.PaymentMode.UPI)
                .paymentDate(LocalDateTime.now())
                .recordedBy(1L)
                .build();
        o.getPaymentRecords().add(payment);

        return o;
    }

    @Nested
    @DisplayName("prepareInvoiceData - data correctness")
    class PrepareInvoiceData {

        @Test
        @DisplayName("Should populate vendor, customer and order header fields")
        void shouldPopulateHeaderFields() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice).isNotNull();
            assertThat(invoice.getInvoiceNumber()).startsWith("INV-").endsWith("-0101");
            assertThat(invoice.getInvoiceDate()).isEqualTo(LocalDate.now());

            // Vendor details sourced from CompanyConfig
            assertThat(invoice.getVendorName()).isEqualTo("Ayurveda Wellness Pvt Ltd");
            assertThat(invoice.getVendorGSTIN()).isEqualTo("27AABCU9603R1ZM");
            assertThat(invoice.getVendorAddress()).isEqualTo("456 Industrial Area, Mumbai - 400058");
            assertThat(invoice.getVendorPhone()).isEqualTo("080-12345678");
            assertThat(invoice.getVendorEmail()).isEqualTo("contact@ayurveda.example");
            assertThat(invoice.getBankDetails()).isEqualTo("Bank: HDFC, A/C: 1234567890, IFSC: HDFC0001234");

            // Customer details
            assertThat(invoice.getCustomerName()).isEqualTo("John Doe");
            assertThat(invoice.getCustomerGSTIN()).isEqualTo("27AABCU9603R1ZM");
            assertThat(invoice.getCustomerPhone()).isEqualTo("9876543210");
            assertThat(invoice.getCustomerEmail()).isEqualTo("john@example.com");
            assertThat(invoice.getBillingAddress()).isEqualTo("123 Main Street, Apartment 4B");
            assertThat(invoice.getShippingAddress()).isEqualTo("123 Main Street, Apartment 4B");

            // Order linkage
            assertThat(invoice.getOrderNumber()).isEqualTo("ORD-20240115-0001");
            assertThat(invoice.getOrderDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("Should compute subtotal, taxable amount, totals and discount")
        void shouldComputeTotals() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            // subtotal = 580 + 370 = 950.00 (sum of line taxable amounts)
            assertThat(invoice.getSubtotal()).isEqualByComparingTo("950.00");
            assertThat(invoice.getTaxableAmount()).isEqualByComparingTo("950.00");
            assertThat(invoice.getTotalTax()).isEqualByComparingTo("85.50");
            assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("50.00");
            assertThat(invoice.getShippingCharge()).isEqualByComparingTo("50.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1085.50");
        }

        @Test
        @DisplayName("Should split GST into equal CGST and SGST for an intra-state sale")
        void shouldSplitGstForIntraStateSale() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getCgstAmount()).isEqualByComparingTo("42.75");
            assertThat(invoice.getSgstAmount()).isEqualByComparingTo("42.75");
            assertThat(invoice.getIgstAmount()).isEqualByComparingTo("0.00");
            // CGST + SGST must reconcile to the total tax
            assertThat(invoice.getCgstAmount().add(invoice.getSgstAmount()))
                    .isEqualByComparingTo(invoice.getTotalTax());
        }

        @Test
        @DisplayName("Should charge IGST for an inter-state sale")
        void shouldChargeIgstForInterStateSale() {
            // Customer in a different state (GST code 29 - Karnataka)
            customer.setGstin("29AABCU9603R1ZM");
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getIgstAmount()).isEqualByComparingTo("85.50");
            assertThat(invoice.getCgstAmount()).isEqualByComparingTo("0.00");
            assertThat(invoice.getSgstAmount()).isEqualByComparingTo("0.00");
            assertThat(invoice.getIgstAmount()).isEqualByComparingTo(invoice.getTotalTax());
        }

        @Test
        @DisplayName("Should default to intra-state (CGST/SGST) when customer GSTIN is absent")
        void shouldDefaultToIntraStateWhenCustomerGstinMissing() {
            customer.setGstin(null);
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getCgstAmount()).isEqualByComparingTo("42.75");
            assertThat(invoice.getSgstAmount()).isEqualByComparingTo("42.75");
            assertThat(invoice.getIgstAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Should compute paid and balance amounts from payment records")
        void shouldComputePaidAndBalance() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getPaidAmount()).isEqualByComparingTo("1000.00");
            assertThat(invoice.getBalanceAmount()).isEqualByComparingTo("85.50");
            assertThat(invoice.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PARTIAL);
        }

        @Test
        @DisplayName("Should report zero paid and full balance when no payments recorded")
        void shouldHandleNoPayments() {
            order.getPaymentRecords().clear();
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0.00");
            assertThat(invoice.getBalanceAmount()).isEqualByComparingTo("1085.50");
        }

        @Test
        @DisplayName("Should map line items including HSN, unit, GST rate (configured and derived)")
        void shouldMapLineItems() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getItems()).hasSize(2);

            InvoiceItemDTO line1 = invoice.getItems().get(0);
            assertThat(line1.getProductName()).isEqualTo("Ashwagandha Capsules");
            assertThat(line1.getSku()).isEqualTo("ASH-001");
            assertThat(line1.getHsnCode()).isEqualTo("30049011");
            assertThat(line1.getUnit()).isEqualTo("BTL");
            assertThat(line1.getQuantity()).isEqualTo(2);
            assertThat(line1.getUnitPrice()).isEqualByComparingTo("300.00");
            assertThat(line1.getDiscountAmount()).isEqualByComparingTo("20.00");
            assertThat(line1.getTaxableAmount()).isEqualByComparingTo("580.00");
            assertThat(line1.getGstAmount()).isEqualByComparingTo("52.20");
            // Configured product GST rate is preferred
            assertThat(line1.getGstRate()).isEqualByComparingTo("18.00");
            assertThat(line1.getTotalAmount()).isEqualByComparingTo("632.20");
            // discountPercentage = 20 / 600 * 100 = 3.33
            assertThat(line1.getDiscountPercentage()).isEqualByComparingTo("3.33");

            InvoiceItemDTO line2 = invoice.getItems().get(1);
            assertThat(line2.getProductName()).isEqualTo("Triphala Powder");
            assertThat(line2.getSku()).isEqualTo("TRI-002");
            // No product association -> HSN null, unit defaults to PCS
            assertThat(line2.getHsnCode()).isNull();
            assertThat(line2.getUnit()).isEqualTo("PCS");
            assertThat(line2.getTaxableAmount()).isEqualByComparingTo("370.00");
            assertThat(line2.getGstAmount()).isEqualByComparingTo("33.30");
            // Derived rate = 33.30 / 370.00 * 100 = 9.00
            assertThat(line2.getGstRate()).isEqualByComparingTo("9.00");
            assertThat(line2.getTotalAmount()).isEqualByComparingTo("403.30");
        }

        @Test
        @DisplayName("Should fall back to default terms when none configured")
        void shouldFallBackToDefaultTerms() {
            companyConfig.setTermsAndConditions(null);
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getTermsAndConditions())
                    .isNotEmpty()
                    .contains("Goods once sold will not be taken back or exchanged.");
        }

        @Test
        @DisplayName("Should split configured terms into trimmed non-empty lines")
        void shouldSplitConfiguredTerms() {
            companyConfig.setTermsAndConditions("  Payment due in 15 days.  \n\n Subject to jurisdiction. \n");
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            assertThat(invoice.getTermsAndConditions())
                    .containsExactly("Payment due in 15 days.", "Subject to jurisdiction.");
        }
    }

    @Nested
    @DisplayName("prepareInvoiceData - exception cases")
    class PrepareInvoiceDataExceptions {

        @Test
        @DisplayName("Should throw IllegalArgumentException when order id is null")
        void shouldThrowWhenOrderIdNull() {
            assertThatThrownBy(() -> billingExportService.prepareInvoiceData(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Order ID cannot be null");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when order does not exist")
        void shouldThrowWhenOrderMissing() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingExportService.prepareInvoiceData(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when order is in NEW status")
        void shouldThrowWhenOrderNew() {
            Order newOrder = buildOrder(Order.OrderStatus.NEW);
            when(orderRepository.findById(101L)).thenReturn(Optional.of(newOrder));

            assertThatThrownBy(() -> billingExportService.prepareInvoiceData(101L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NEW");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when order is in CONFIRMED status")
        void shouldThrowWhenOrderConfirmed() {
            Order confirmedOrder = buildOrder(Order.OrderStatus.CONFIRMED);
            when(orderRepository.findById(101L)).thenReturn(Optional.of(confirmedOrder));

            assertThatThrownBy(() -> billingExportService.prepareInvoiceData(101L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CONFIRMED");
        }
    }

    @Nested
    @DisplayName("PDF rendering")
    class PdfRendering {

        @Test
        @DisplayName("renderInvoicePdf should produce non-empty bytes beginning with %PDF")
        void renderInvoicePdfProducesValidPdf() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);
            InvoiceDTO invoice = billingExportService.prepareInvoiceData(101L);

            byte[] pdfBytes = billingExportService.renderInvoicePdf(invoice);

            assertValidPdf(pdfBytes);
        }

        @Test
        @DisplayName("generateInvoicePdf should produce non-empty bytes beginning with %PDF")
        void generateInvoicePdfProducesValidPdf() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            byte[] pdfBytes = billingExportService.generateInvoicePdf(101L);

            assertValidPdf(pdfBytes);
        }

        @Test
        @DisplayName("generateInvoicePdf should render an inter-state (IGST) invoice")
        void generateInvoicePdfForInterStateInvoice() {
            customer.setGstin("29AABCU9603R1ZM");
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(configurationService.getConfiguration()).thenReturn(companyConfig);

            byte[] pdfBytes = billingExportService.generateInvoicePdf(101L);

            assertValidPdf(pdfBytes);
        }

        @Test
        @DisplayName("renderInvoicePdf should throw IllegalArgumentException for null invoice")
        void renderInvoicePdfRejectsNull() {
            assertThatThrownBy(() -> billingExportService.renderInvoicePdf(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invoice data cannot be null");
        }

        private void assertValidPdf(byte[] pdfBytes) {
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
            String header = new String(
                    Arrays.copyOfRange(pdfBytes, 0, Math.min(4, pdfBytes.length)),
                    StandardCharsets.US_ASCII);
            assertThat(header).isEqualTo("%PDF");
        }
    }
}
