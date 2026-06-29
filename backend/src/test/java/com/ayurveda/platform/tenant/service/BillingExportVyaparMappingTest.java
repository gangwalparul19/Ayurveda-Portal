package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.VyaparInvoiceDTO;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.BillingExportRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BillingExportService Vyapar format mapping functionality.
 * Tests the mapOrderToVyaparFormat method which converts Order entities to Vyapar-compatible format.
 * 
 * Tests Requirement 16: Vyapar Billing Export
 * - 16.1: Map order data to Vyapar CSV format
 * - 16.2: Include customer name, address, product details, quantities, prices, and totals
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Export - Vyapar Format Mapping Tests")
class BillingExportVyaparMappingTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BillingExportRepository billingExportRepository;

    @InjectMocks
    private BillingExportService billingExportService;

    private Order testOrder;
    private Customer testCustomer;
    private List<OrderItem> testOrderItems;

    @BeforeEach
    void setUp() {
        // Setup test customer with complete address
        testCustomer = Customer.builder()
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

        // Setup test order
        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20240115-0001")
                .customer(testCustomer)
                .salespersonId(1L)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("1000.00"))
                .discountAmount(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("85.50"))
                .shippingCharge(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("1085.50"))
                .paymentMode(Order.PaymentMode.UPI)
                .paymentStatus(Order.PaymentStatus.PAID)
                .orderDate(LocalDate.of(2024, 1, 15))
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        // Setup test order items
        testOrderItems = new ArrayList<>();
        
        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .order(testOrder)
                .productNameSnapshot("Ashwagandha Capsules")
                .skuSnapshot("ASH-001")
                .quantity(2)
                .unitPrice(new BigDecimal("300.00"))
                .mrpSnapshot(new BigDecimal("350.00"))
                .discount(new BigDecimal("20.00"))
                .taxAmount(new BigDecimal("52.20"))
                .lineTotal(new BigDecimal("632.20"))
                .build();
        
        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .order(testOrder)
                .productNameSnapshot("Triphala Powder")
                .skuSnapshot("TRI-002")
                .quantity(1)
                .unitPrice(new BigDecimal("400.00"))
                .mrpSnapshot(new BigDecimal("450.00"))
                .discount(new BigDecimal("30.00"))
                .taxAmount(new BigDecimal("33.30"))
                .lineTotal(new BigDecimal("403.30"))
                .build();
        
        testOrderItems.add(item1);
        testOrderItems.add(item2);
        testOrder.getItems().addAll(testOrderItems);
    }

    @Test
    @DisplayName("Should map order to Vyapar format with all required fields")
    void testMapOrderToVyaparFormat_AllFields() {
        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - Order identification
        assertNotNull(result, "Result should not be null");
        assertEquals("ORD-20240115-0001", result.getInvoiceNumber());
        assertEquals(LocalDate.of(2024, 1, 15), result.getInvoiceDate());

        // Assert - Customer information (Requirement 16.2)
        assertEquals("John Doe", result.getCustomerName());
        assertEquals("9876543210", result.getCustomerPhone());
        assertEquals("john@example.com", result.getCustomerEmail());
        assertEquals("123 Main Street, Apartment 4B", result.getShippingAddress());
        assertEquals("Mumbai", result.getCity());
        assertEquals("Maharashtra", result.getState());
        assertEquals("400001", result.getPincode());
        assertEquals("27AABCU9603R1ZM", result.getGstin());

        // Assert - Financial details (Requirement 16.2 - totals)
        assertEquals(new BigDecimal("1000.00"), result.getSubtotal());
        assertEquals(new BigDecimal("50.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("85.50"), result.getTaxAmount());
        assertEquals(new BigDecimal("50.00"), result.getShippingCharge());
        assertEquals(new BigDecimal("1085.50"), result.getTotalAmount());

        // Assert - Payment information
        assertEquals("UPI", result.getPaymentMode());
        assertEquals("PAID", result.getPaymentStatus());

        // Assert - Order metadata
        assertEquals("MANUAL", result.getOrderSource());
        assertEquals("DELIVERED", result.getOrderStatus());

        // Assert - Line items (Requirement 16.2 - product details, quantities, prices)
        assertNotNull(result.getLineItems());
        assertEquals(2, result.getLineItems().size());
    }

    @Test
    @DisplayName("Should map product line items correctly with all details")
    void testMapOrderToVyaparFormat_LineItems() {
        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - First line item (Requirement 16.2 - product details, quantities, prices)
        VyaparInvoiceDTO.VyaparInvoiceLineItemDTO item1 = result.getLineItems().get(0);
        assertEquals("Ashwagandha Capsules", item1.getProductName());
        assertEquals("ASH-001", item1.getSku());
        assertEquals(2, item1.getQuantity());
        assertEquals(new BigDecimal("300.00"), item1.getUnitPrice());
        assertEquals(new BigDecimal("350.00"), item1.getMrp());
        assertEquals(new BigDecimal("20.00"), item1.getDiscount());
        assertEquals(new BigDecimal("52.20"), item1.getTaxAmount());
        assertEquals(new BigDecimal("632.20"), item1.getLineTotal());

        // Assert - Second line item
        VyaparInvoiceDTO.VyaparInvoiceLineItemDTO item2 = result.getLineItems().get(1);
        assertEquals("Triphala Powder", item2.getProductName());
        assertEquals("TRI-002", item2.getSku());
        assertEquals(1, item2.getQuantity());
        assertEquals(new BigDecimal("400.00"), item2.getUnitPrice());
        assertEquals(new BigDecimal("450.00"), item2.getMrp());
        assertEquals(new BigDecimal("30.00"), item2.getDiscount());
        assertEquals(new BigDecimal("33.30"), item2.getTaxAmount());
        assertEquals(new BigDecimal("403.30"), item2.getLineTotal());
    }

    @Test
    @DisplayName("Should handle order with null customer gracefully")
    void testMapOrderToVyaparFormat_NullCustomer() {
        // Arrange
        testOrder.setCustomer(null);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - Customer fields should be empty strings
        assertNotNull(result);
        assertEquals("", result.getCustomerName());
        assertEquals("", result.getCustomerPhone());
        assertEquals("", result.getCustomerEmail());
        assertEquals("", result.getShippingAddress());
        assertEquals("", result.getCity());
        assertEquals("", result.getState());
        assertEquals("", result.getPincode());
        assertEquals("", result.getGstin());
    }

    @Test
    @DisplayName("Should handle customer with incomplete address")
    void testMapOrderToVyaparFormat_IncompleteAddress() {
        // Arrange - Customer with only addressLine1
        testCustomer.setAddressLine2(null);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - Address should only contain addressLine1
        assertEquals("123 Main Street", result.getShippingAddress());
    }

    @Test
    @DisplayName("Should handle customer with empty address fields")
    void testMapOrderToVyaparFormat_EmptyAddress() {
        // Arrange - Customer with empty address fields
        testCustomer.setAddressLine1("");
        testCustomer.setAddressLine2("");

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - Address should be empty
        assertEquals("", result.getShippingAddress());
    }

    @Test
    @DisplayName("Should handle order with null optional financial fields")
    void testMapOrderToVyaparFormat_NullOptionalFields() {
        // Arrange - Set optional fields to null
        testOrder.setDiscountAmount(null);
        testOrder.setTaxAmount(null);
        testOrder.setShippingCharge(null);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - Should default to zero
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, result.getTaxAmount());
        assertEquals(BigDecimal.ZERO, result.getShippingCharge());
    }

    @Test
    @DisplayName("Should handle order items with null optional fields")
    void testMapOrderToVyaparFormat_NullItemFields() {
        // Arrange - Set optional item fields to null
        testOrderItems.get(0).setDiscount(null);
        testOrderItems.get(0).setTaxAmount(null);
        testOrderItems.get(0).setMrpSnapshot(null);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert - Should default to zero
        VyaparInvoiceDTO.VyaparInvoiceLineItemDTO item = result.getLineItems().get(0);
        assertEquals(BigDecimal.ZERO, item.getDiscount());
        assertEquals(BigDecimal.ZERO, item.getTaxAmount());
        assertNull(item.getMrp());
    }

    @Test
    @DisplayName("Should handle order with null payment mode and status")
    void testMapOrderToVyaparFormat_NullPaymentInfo() {
        // Arrange
        testOrder.setPaymentMode(null);
        testOrder.setPaymentStatus(null);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertEquals("", result.getPaymentMode());
        assertEquals("", result.getPaymentStatus());
    }

    @Test
    @DisplayName("Should handle order with empty items list")
    void testMapOrderToVyaparFormat_EmptyItems() {
        // Arrange
        testOrder.getItems().clear();

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertNotNull(result.getLineItems());
        assertTrue(result.getLineItems().isEmpty());
    }

    @Test
    @DisplayName("Should throw exception when order is null")
    void testMapOrderToVyaparFormat_NullOrder() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> billingExportService.mapOrderToVyaparFormat(null)
        );
        
        assertEquals("Order cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should map WhatsApp order source correctly")
    void testMapOrderToVyaparFormat_WhatsAppOrder() {
        // Arrange
        testOrder.setOrderSource(Order.OrderSource.WHATSAPP);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertEquals("WHATSAPP", result.getOrderSource());
    }

    @Test
    @DisplayName("Should map Storefront order source correctly")
    void testMapOrderToVyaparFormat_StorefrontOrder() {
        // Arrange
        testOrder.setOrderSource(Order.OrderSource.STOREFRONT);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertEquals("STOREFRONT", result.getOrderSource());
    }

    @Test
    @DisplayName("Should handle customer with only addressLine2")
    void testMapOrderToVyaparFormat_OnlyAddressLine2() {
        // Arrange
        testCustomer.setAddressLine1(null);
        testCustomer.setAddressLine2("Building C, Floor 3");

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertEquals("Building C, Floor 3", result.getShippingAddress());
    }

    @Test
    @DisplayName("Should handle customer with whitespace-only address fields")
    void testMapOrderToVyaparFormat_WhitespaceAddress() {
        // Arrange
        testCustomer.setAddressLine1("   ");
        testCustomer.setAddressLine2("   ");

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertEquals("", result.getShippingAddress());
    }

    @Test
    @DisplayName("Should map order with COD payment mode")
    void testMapOrderToVyaparFormat_CODPayment() {
        // Arrange
        testOrder.setPaymentMode(Order.PaymentMode.COD);
        testOrder.setPaymentStatus(Order.PaymentStatus.PENDING);

        // Act
        VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

        // Assert
        assertEquals("COD", result.getPaymentMode());
        assertEquals("PENDING", result.getPaymentStatus());
    }

    @Test
    @DisplayName("Should map order with all status types")
    void testMapOrderToVyaparFormat_AllOrderStatuses() {
        Order.OrderStatus[] statuses = Order.OrderStatus.values();
        
        for (Order.OrderStatus status : statuses) {
            // Arrange
            testOrder.setStatus(status);

            // Act
            VyaparInvoiceDTO result = billingExportService.mapOrderToVyaparFormat(testOrder);

            // Assert
            assertEquals(status.name(), result.getOrderStatus(),
                    "Status should be correctly mapped for " + status.name());
        }
    }
}
