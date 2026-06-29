# Implementation Plan: Ayurveda Order & Dispatch Management System

## Overview

This implementation plan breaks down the Ayurveda Order & Dispatch Management System into discrete coding tasks. The system is built using Java Spring Boot backend with Angular frontend, featuring configuration-based client setup, JWT authentication, WhatsApp order parsing, comprehensive order lifecycle management, dispatch label generation, and reporting capabilities.

The implementation follows a layered approach: database schema and configuration management, core entities and repositories, service layer with business logic, REST controllers, WhatsApp parsing, reporting, and integration. Each task builds incrementally toward a complete working system.

## Tasks

- [x] 1. Set up database schema and configuration management
  - [x] 1.1 Create application database entities (User, Role, AuditLog, CompanyConfig)
    - Implement User entity with role-based permissions
    - Implement Role entity for RBAC
    - Implement AuditLog entity for security tracking
    - Implement CompanyConfig entity for company settings (name, address, logo, business rules)
    - Create corresponding JPA repositories
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 18.1, 32.1_

  - [x] 1.2 Create business database entities (Order, OrderItem, Product, Customer, Salesperson)
    - Implement Order entity with all fields and relationships
    - Implement OrderItem entity with product snapshot fields
    - Implement Product entity with stock tracking
    - Implement Customer entity with address fields
    - Implement Salesperson entity with commission tracking
    - Implement OrderStatusHistory entity for audit trail
    - Implement PaymentRecord entity for payment tracking
    - Implement StockHistory entity for inventory audit
    - Create JPA repositories for all business entities
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 6.1, 7.1, 8.1, 9.1, 10.1, 20.1, 21.1_

  - [x] 1.3 Implement configuration management service
    - Create ConfigurationService to load settings from application.yml
    - Load company details (name, address, phone, email, logo path)
    - Load business rules (low stock threshold, order number prefix, tax rates)
    - Validate configuration on startup
    - Cache configuration in memory for performance
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_

  - [x] 1.4 Implement JWT authentication filters
    - Create JwtAuthenticationFilter to validate JWT tokens
    - Extract user ID and role from JWT claims
    - Implement SecurityConfig with filter chain
    - _Requirements: 18.1, 18.4, 18.5_

- [x] 2. Checkpoint - Ensure database and authentication setup is complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Implement core order management service
  - [x] 3.1 Implement order number generation
    - Create method generateOrderNumber() with format ORD-YYYYMMDD-XXXX
    - Query database for daily order count to determine sequence number
    - Ensure uniqueness with database constraints
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 Implement order total calculation logic
    - Create calculateOrderTotals() method in Order entity
    - Calculate subtotal from sum of OrderItem lineTotals
    - Calculate totalAmount = subtotal - discount + tax + shipping
    - Implement calculateLineTotal() in OrderItem entity
    - Maintain 2 decimal place precision using BigDecimal
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 3.3 Write property test for order total calculation
    - **Property 1: Order Total Consistency**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**
    - Use jqwik to generate random order items, discounts, taxes, shipping charges
    - Assert: totalAmount = subtotal - discountAmount + taxAmount + shippingCharge

  - [x] 3.4 Implement manual order creation
    - Create OrderManagementService.createManualOrder() method
    - Validate customer exists
    - Validate product stock availability
    - Create Order with NEW status
    - Create OrderItems with product snapshots
    - Calculate order totals
    - Create initial OrderStatusHistory record
    - Save order and return OrderResponseDTO
    - _Requirements: 1.1, 1.2, 1.3, 4.1_

  - [x] 3.5 Write unit tests for manual order creation
    - Test successful order creation with valid data
    - Test failure when customer doesn't exist
    - Test failure with insufficient stock
    - Test order number uniqueness
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 3.5 Implement order status transition validation
    - Create isValidStatusTransition() method with state machine rules
    - Define valid transitions map (NEW→CONFIRMED, CONFIRMED→PAID, etc.)
    - Add business rule validations (DISPATCHED requires PAID payment)
    - Add address validation for DISPATCHED status
    - Add stock availability check for PACKED status
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

  - [x] 3.6 Write property test for status transition validation
    - **Property 2: Valid Status Transitions**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8**
    - Generate all combinations of (currentStatus, newStatus) pairs
    - Assert: valid transitions succeed, invalid transitions throw exception

  - [x] 3.7 Implement order status update with audit trail
    - Create updateOrderStatus() method in OrderManagementService
    - Validate status transition
    - Create OrderStatusHistory record
    - Update order status
    - Set dispatchedAt/deliveredAt timestamps
    - Handle stock updates for PACKED/CANCELLED/RETURNED statuses
    - Save changes transactionally
    - _Requirements: 5.9, 5.10, 5.11, 6.1, 6.2, 6.3, 9.2, 9.3_

  - [x] 3.8 Write unit tests for order status transitions
    - Test all valid status transitions
    - Test invalid transition rejection
    - Test timestamp updates for DISPATCHED and DELIVERED
    - Test stock restoration for CANCELLED and RETURNED
    - _Requirements: 5.1-5.11, 6.1-6.3_

- [x] 4. Implement payment recording and tracking
  - [x] 4.1 Implement payment recording logic
    - Create recordPayment() method in OrderManagementService
    - Validate payment amount doesn't exceed remaining balance
    - Create PaymentRecord entity with transaction details
    - Calculate total paid amount
    - Update order paymentStatus (PENDING/PARTIAL/PAID)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 4.2 Write property test for payment balance
    - **Property 4: Payment Balance**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7**
    - Generate random order totals and payment sequences
    - Assert: sum of payments never exceeds order total
    - Assert: payment status correctly derived from total paid

  - [x] 4.3 Write unit tests for payment recording
    - Test successful payment recording
    - Test payment exceeding order total is rejected
    - Test payment status updates correctly
    - Test multiple partial payments
    - _Requirements: 7.1-7.7_

- [x] 5. Checkpoint - Ensure core order and payment logic is complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement product and stock management
  - [x] 6.1 Implement product CRUD operations
    - Create ProductManagementService with create/update/delete methods
    - Ensure SKU uniqueness validation
    - Implement product retrieval by ID and SKU
    - Implement product search and filtering
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 6.2 Implement stock management with history
    - Create updateStock() method with operation type (STOCK_IN, STOCK_OUT, ADJUSTMENT, RETURN)
    - Create StockHistory record for each stock change
    - Validate stock never becomes negative
    - Implement getLowStockProducts() query
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 21.1, 21.2, 21.3, 21.4_

  - [x] 6.3 Write property test for stock consistency
    - **Property 3: Stock Consistency**
    - **Validates: Requirements 9.2, 9.3, 9.4, 9.6**
    - Generate random stock operations (in/out/adjustment)
    - Assert: stock quantity = initial - outflows + inflows
    - Assert: stock never negative

  - [x] 6.4 Write unit tests for stock management
    - Test stock increase and decrease operations
    - Test negative stock prevention
    - Test stock history creation
    - Test low stock product query
    - _Requirements: 9.1-9.6, 21.1-21.4_

- [x] 7. Implement customer management
  - [x] 7.1 Implement customer CRUD operations
    - Create CustomerManagementService with create/update/delete methods
    - Implement customer search by phone and name
    - Implement findOrCreateCustomer() for WhatsApp orders
    - Implement duplicate detection by phone number
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 7.2 Write unit tests for customer management
    - Test customer creation and retrieval
    - Test phone number search
    - Test findOrCreateCustomer logic
    - Test duplicate detection
    - _Requirements: 10.1-10.5_

- [x] 8. Implement WhatsApp message parsing
  - [x] 8.1 Implement customer information extraction
    - Create extractCustomerInfo() method with regex patterns
    - Extract name using "Name:" or "Customer:" patterns
    - Extract Indian phone numbers (10 digits with optional +91/0 prefix)
    - Extract address and pincode
    - _Requirements: 3.1, 3.2_

  - [x] 8.2 Implement product extraction with fuzzy matching
    - Create extractProducts() method to parse product lines
    - Use regex to identify product identifier and quantity patterns
    - Implement fuzzyMatchProduct() using Levenshtein distance
    - Try exact SKU match first, then fuzzy name matching
    - Use 0.6 threshold for fuzzy matches
    - _Requirements: 3.2, 26.1, 26.2, 26.3, 26.4, 26.5_

  - [x] 8.3 Implement payment information extraction
    - Create extractPaymentInfo() method to detect payment mode
    - Use regex to identify COD, UPI, BANK_TRANSFER, ONLINE, CREDIT keywords
    - Extract payment amount if present
    - Default to COD if not detected
    - _Requirements: 3.3_

  - [x] 8.4 Implement main WhatsApp parsing with confidence scoring
    - Create parseWhatsAppMessage() method orchestrating all extraction
    - Calculate confidence score based on extracted data completeness
    - Reduce confidence for missing customer info (-0.3)
    - Reduce confidence for missing products (-0.4)
    - Reduce confidence for low product match scores
    - Collect warnings for parsing issues
    - _Requirements: 3.4, 3.5, 3.6_

  - [x] 8.5 Write property test for WhatsApp parsing confidence
    - **Property 8: WhatsApp Parsing Confidence**
    - **Validates: Requirements 3.4, 3.5**
    - Generate various WhatsApp message formats
    - Assert: confidence score always between 0.0 and 1.0
    - Assert: confidence > 0 implies some data extracted

  - [x] 8.6 Write unit tests for WhatsApp parser
    - Test customer info extraction with various formats
    - Test product extraction and fuzzy matching
    - Test payment info detection
    - Test confidence scoring
    - Test warning generation for missing data
    - _Requirements: 3.1-3.6, 26.1-26.5_

  - [x] 8.7 Implement WhatsApp order creation
    - Create createWhatsAppOrder() method in OrderManagementService
    - Parse WhatsApp text using parser service
    - Apply manual overrides if provided
    - Find or create customer
    - Create order with WHATSAPP source
    - Store raw WhatsApp text in order
    - Return order with confidence score and warnings
    - _Requirements: 1.2, 1.3, 3.6_

  - [x] 8.8 Write integration test for WhatsApp order creation
    - Test end-to-end WhatsApp order processing
    - Test customer auto-creation
    - Test product matching
    - Test order creation with parsed data
    - _Requirements: 1.2, 1.3, 3.1-3.6_

- [x] 9. Checkpoint - Ensure WhatsApp parsing and order creation is complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement duplicate order detection
  - [x] 10.1 Implement duplicate check algorithm
    - Create checkDuplicate() method in OrderManagementService
    - Find customer by phone
    - Query recent orders within 7-day window
    - Calculate Jaccard similarity for product sets
    - Flag orders with 80% or higher similarity
    - Return duplicate check result with similarity scores
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

  - [x] 10.2 Write property test for duplicate detection accuracy
    - **Property 5: Duplicate Detection Accuracy**
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5**
    - Generate orders with varying product overlap
    - Assert: 80%+ overlap flagged as duplicate
    - Assert: <80% overlap not flagged
    - Assert: orders outside 7-day window ignored

  - [x] 10.3 Write unit tests for duplicate detection
    - Test exact duplicate detection
    - Test partial overlap scenarios
    - Test time window filtering
    - Test new customer (no duplicates)
    - _Requirements: 11.1-11.5_

- [x] 11. Implement salesperson management
  - [x] 11.1 Implement salesperson CRUD operations
    - Create SalespersonManagementService
    - Implement create/update/delete methods
    - Ensure employee code uniqueness
    - Validate commission rate (0-100)
    - Link to PlatformUser with SALESPERSON role
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5_

  - [x] 11.2 Write unit tests for salesperson management
    - Test salesperson creation
    - Test employee code uniqueness
    - Test commission rate validation
    - Test status management (ACTIVE, INACTIVE, ON_LEAVE)
    - _Requirements: 20.1-20.5_

- [x] 12. Implement dispatch label generation
  - [x] 12.1 Implement label data preparation
    - Create prepareLabelData() method in DispatchLabelService
    - Extract order details (number, date)
    - Extract customer shipping information
    - Extract product list with quantities
    - Calculate total items and weight
    - Include vendor information
    - Generate barcode string for order number
    - _Requirements: 12.1, 12.2, 12.3, 12.5_

  - [x] 12.2 Implement barcode generation
    - Integrate barcode library (e.g., Barcode4J or ZXing)
    - Generate Code128 barcode for order number
    - Return barcode as image
    - _Requirements: 12.4_

  - [x] 12.3 Implement PDF label generation
    - Use PDF library (iText or Apache PDFBox)
    - Create generateSingleLabel() method
    - Render label with order, customer, product, and barcode
    - Format label on A4 page
    - Return PDF as byte array
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.7_

  - [x] 12.4 Implement bulk label generation
    - Create generateBulkLabels() method
    - Iterate through order IDs
    - Generate label page for each order
    - Combine into single PDF document
    - _Requirements: 12.6, 12.7_

  - [x] 12.5 Write unit tests for dispatch label generation
    - Test label data preparation
    - Test barcode generation
    - Test single PDF generation
    - Test bulk PDF generation
    - Test PDF size constraint (<500KB per label)
    - _Requirements: 12.1-12.7_

- [x] 13. Checkpoint - Ensure duplicate detection, salesperson, and dispatch labels are complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implement reporting and analytics
  - [x] 14.1 Implement daily and monthly sales reports
    - Create ReportService with getDailySalesReport() method
    - Query orders by order date
    - Calculate total orders, total sales amount, delivered count, cancelled count
    - Only count DELIVERED orders in sales amount
    - Implement getMonthlySalesReport() with month parameter
    - _Requirements: 13.1, 13.2, 13.3, 13.4_

  - [x] 14.2 Implement salesperson sales reports
    - Create getSalespersonSalesReport() method
    - Filter orders by salesperson ID and date range
    - Calculate sales metrics per salesperson
    - _Requirements: 13.5, 20.5_

  - [x] 14.3 Implement product-wise reports
    - Create getProductWiseSalesReport() method
    - Aggregate sales by product for date range
    - Include product name, total quantity sold, total sales amount
    - Implement getTopSellingProducts() with limit parameter
    - _Requirements: 14.1, 14.2, 14.5_

  - [x] 14.4 Implement geographic reports
    - Create getStateWiseSalesReport() method
    - Aggregate sales by customer state
    - Include state name, order count, total sales amount
    - Implement getCityWiseSalesReport() with state filter
    - _Requirements: 14.3, 14.4_

  - [x] 14.5 Write unit tests for reporting
    - Test daily sales report calculation
    - Test monthly sales report calculation
    - Test salesperson report filtering
    - Test product-wise aggregation
    - Test state-wise aggregation
    - _Requirements: 13.1-13.5, 14.1-14.5_

- [x] 15. Implement report export functionality
  - [x] 15.1 Implement CSV export
    - Create exportReportToCSV() method
    - Format report data as comma-separated values with headers
    - Set appropriate content-type header (text/csv)
    - _Requirements: 15.1, 15.4_

  - [x] 15.2 Implement Excel export
    - Integrate Apache POI library
    - Create exportReportToExcel() method
    - Generate XLSX file with formatted data
    - Set appropriate content-type header
    - _Requirements: 15.2, 15.4_

  - [x] 15.3 Implement PDF export
    - Create exportReportToPDF() method
    - Generate formatted PDF report document
    - Set appropriate content-type header
    - _Requirements: 15.3, 15.4_

  - [x] 15.4 Write unit tests for report export
    - Test CSV generation and format
    - Test Excel generation
    - Test PDF generation
    - Test content-type headers
    - _Requirements: 15.1-15.4_

- [x] 16. Implement Vyapar billing export
  - [x] 16.1 Implement Vyapar format mapping
    - Create mapOrderToVyaparFormat() method
    - Map order fields to Vyapar CSV format
    - Include customer details, products, quantities, prices, totals
    - _Requirements: 16.1, 16.2_

  - [x] 16.2 Implement Vyapar export methods
    - Create exportToVyaparFormat() for selected orders
    - Create exportDailyOrdersToVyapar() for specific date
    - Create exportMonthlyOrdersToVyapar() for specific month
    - Generate CSV in Vyapar-compatible format
    - _Requirements: 16.1, 16.2, 16.3, 16.4_

  - [x] 16.3 Write unit tests for Vyapar export
    - Test order to Vyapar format mapping
    - Test daily export
    - Test monthly export
    - Test CSV format correctness
    - _Requirements: 16.1-16.4_

- [x] 17. Checkpoint - Ensure reporting and export functionality is complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 18. Implement REST API controllers
  - [x] 18.1 Implement OrderController
    - Create POST /api/orders/manual endpoint
    - Create POST /api/orders/whatsapp endpoint
    - Create POST /api/orders/storefront endpoint
    - Create GET /api/orders/{orderId} endpoint
    - Create GET /api/orders with pagination and filters
    - Create PATCH /api/orders/{orderId}/status endpoint
    - Create PATCH /api/orders/{orderId}/payment endpoint
    - Create POST /api/orders/check-duplicate endpoint
    - Create POST /api/orders/{orderId}/cancel endpoint
    - Add JWT authentication on all endpoints
    - Add request validation
    - _Requirements: 1.1, 1.2, 1.3, 5.9, 7.1, 11.1, 22.1-22.6, 27.1-27.4_

  - [x] 18.2 Implement ProductController
    - Create POST /api/products endpoint
    - Create PUT /api/products/{productId} endpoint
    - Create DELETE /api/products/{productId} endpoint
    - Create GET /api/products/{productId} endpoint
    - Create GET /api/products with pagination and filters
    - Create PATCH /api/products/{productId}/stock endpoint
    - Create GET /api/products/low-stock endpoint
    - Add authentication and validation
    - _Requirements: 8.1-8.5, 9.1-9.6_

  - [x] 18.3 Implement CustomerController
    - Create POST /api/customers endpoint
    - Create PUT /api/customers/{customerId} endpoint
    - Create GET /api/customers/{customerId} endpoint
    - Create GET /api/customers with search
    - Create GET /api/customers/{customerId}/orders endpoint
    - Add authentication and validation
    - _Requirements: 10.1-10.5, 24.1-24.3_

  - [x] 18.4 Implement SalespersonController
    - Create POST /api/salespersons endpoint
    - Create PUT /api/salespersons/{salespersonId} endpoint
    - Create GET /api/salespersons/{salespersonId} endpoint
    - Create GET /api/salespersons with filters
    - Add authentication and validation
    - _Requirements: 20.1-20.5_

  - [x] 18.5 Implement DispatchLabelController
    - Create GET /api/dispatch/labels/{orderId} endpoint
    - Create POST /api/dispatch/labels/bulk endpoint
    - Return PDF with application/pdf content-type
    - Add authentication
    - _Requirements: 12.1-12.7_

  - [x] 18.6 Implement ReportController
    - Create GET /api/reports/sales/daily endpoint
    - Create GET /api/reports/sales/monthly endpoint
    - Create GET /api/reports/sales/salesperson/{salespersonId} endpoint
    - Create GET /api/reports/products endpoint
    - Create GET /api/reports/geographic/state endpoint
    - Create GET /api/reports/geographic/city endpoint
    - Create GET /api/reports/export endpoint with format parameter
    - Add authentication
    - _Requirements: 13.1-13.5, 14.1-14.5, 15.1-15.4_

  - [x] 18.7 Implement BillingController
    - Create POST /api/billing/export/vyapar endpoint
    - Create GET /api/billing/invoice/{orderId} endpoint
    - Add authentication
    - _Requirements: 16.1-16.4, 29.1-29.4_

  - [x] 18.8 Implement AuthController
    - Create POST /api/auth/login endpoint
    - Validate credentials against User entity
    - Generate JWT token with user ID, role, permissions
    - Set token expiration (24 hours for access, 7 days for refresh)
    - Create POST /api/auth/refresh endpoint
    - _Requirements: 18.1, 18.2, 18.3_

  - [x] 18.9 Write integration tests for REST APIs
    - Test order creation endpoints
    - Test order status update endpoints
    - Test product management endpoints
    - Test customer management endpoints
    - Test report generation endpoints
    - Test dispatch label generation endpoints
    - Test authentication flows
    - _Requirements: All API-related requirements_

- [x] 19. Implement role-based access control
  - [x] 19.1 Implement permission checking in SecurityConfig
    - Define role-permission mappings (ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT)
    - Configure endpoint access rules based on roles
    - SALESPERSON: allow order creation, status updates; restrict user management
    - DISPATCHER: allow viewing PAID/PACKED orders, generating labels
    - ACCOUNTANT: allow payment recording, report generation
    - Return 403 Forbidden for unauthorized access
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5_

  - [x] 19.2 Write integration tests for RBAC
    - Test SALESPERSON access permissions
    - Test DISPATCHER access permissions
    - Test ACCOUNTANT access permissions
    - Test unauthorized access returns 403
    - _Requirements: 19.1-19.5_

- [x] 20. Implement error handling and validation
  - [x] 20.1 Implement GlobalExceptionHandler
    - Handle OrderNotFoundException
    - Handle InvalidStatusTransitionException
    - Handle InsufficientStockException
    - Handle PaymentExceedsTotalException
    - Handle UnauthorizedException
    - Handle TenantNotFoundException
    - Map exceptions to appropriate HTTP status codes and error messages
    - _Requirements: 25.1, 25.2, 25.3, 25.4_

  - [x] 20.2 Implement input validation
    - Add validation annotations to DTOs (@NotNull, @NotBlank, @Size, @BigRange)
    - Validate phone numbers (Indian format: 10 digits with optional +91/0)
    - Validate email addresses (RFC 5322 format)
    - Validate monetary amounts (non-negative)
    - Validate dates (order date not in future)
    - Sanitize HTML content in text fields
    - _Requirements: 31.1, 31.2, 31.3, 31.4, 31.5_

  - [x] 20.3 Write unit tests for error handling
    - Test exception mapping to HTTP status codes
    - Test error message formatting
    - Test input validation
    - _Requirements: 25.1-25.4, 31.1-31.5_

- [x] 21. Implement audit logging
  - [x] 21.1 Implement audit logging for authentication events
    - Log successful logins with timestamp, user ID, IP address
    - Log failed login attempts with username and IP address
    - Store logs in AuditLog entity in application database
    - _Requirements: 32.1, 32.2_

  - [x] 21.2 Implement audit logging for payment and order events
    - Payment recording already logs user ID in PaymentRecord
    - Order status changes already logged in OrderStatusHistory
    - Ensure all significant actions include user ID and timestamp
    - _Requirements: 32.3, 32.4_

  - [x] 21.3 Write unit tests for audit logging
    - Test login audit logging
    - Test failed login audit logging
    - Test payment audit in PaymentRecord
    - Test order status audit in OrderStatusHistory
    - _Requirements: 32.1-32.4_

- [x] 22. Checkpoint - Ensure controllers, RBAC, error handling, and audit logging are complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 23. Implement storefront order creation
  - [x] 23.1 Implement public storefront endpoint
    - Create createStorefrontOrder() method in OrderManagementService
    - Allow order creation without authentication
    - Validate product availability
    - Auto-create customer from order details
    - Set order source to STOREFRONT
    - Return order confirmation
    - _Requirements: 1.3_

  - [x] 23.2 Write integration test for storefront orders
    - Test order creation without authentication
    - Test customer auto-creation
    - Test product validation
    - _Requirements: 1.3_

- [x] 24. Implement bulk operations
  - [x] 24.1 Implement bulk order status update
    - Create bulkUpdateStatus() method in OrderManagementService
    - Accept list of order IDs and target status
    - Validate each order individually
    - Continue processing on individual failures
    - Return partial success results
    - _Requirements: 23.1, 23.2, 23.3, 23.4_

  - [x] 24.2 Write unit tests for bulk operations
    - Test bulk status update with all valid orders
    - Test bulk status update with some invalid orders
    - Test partial success handling
    - _Requirements: 23.1-23.4_

- [x] 25. Implement invoice generation
  - [x] 25.1 Implement invoice data preparation
    - Create prepareInvoiceData() method
    - Include vendor details, customer details, order items
    - Calculate tax breakdowns and totals
    - _Requirements: 29.1, 29.2_

  - [x] 25.2 Implement invoice PDF generation
    - Create generateInvoicePDF() method
    - Use PDF library to format invoice document
    - Include all invoice data and calculations
    - Return PDF as byte array
    - _Requirements: 29.1, 29.2, 29.3, 29.4_

  - [x] 25.3 Write unit tests for invoice generation
    - Test invoice data preparation
    - Test PDF generation
    - Test tax calculations
    - _Requirements: 29.1-29.4_

- [x] 26. Implement order return processing
  - [x] 26.1 Implement return order logic
    - Create returnOrder() method in OrderManagementService
    - Transition status to RETURNED
    - Restore stock quantities
    - Record return reason in status history
    - _Requirements: 28.1, 28.2, 28.3, 28.4_

  - [x] 26.2 Write unit tests for return processing
    - Test return from DELIVERED status
    - Test stock restoration
    - Test return reason recording
    - _Requirements: 28.1-28.4_

- [x] 27. Implement customer order history and analytics
  - [x] 27.1 Implement customer order history retrieval
    - Create getCustomerOrderHistory() method
    - Return all orders for customer ordered by date descending
    - Include order count, total amount spent, average order value
    - _Requirements: 24.1, 24.2_

  - [x] 27.2 Implement customer lifetime value calculation
    - Create getCustomerLifetimeValue() method
    - Calculate sum of all DELIVERED order totals for customer
    - _Requirements: 24.3_

  - [x] 27.3 Write unit tests for customer analytics
    - Test order history retrieval
    - Test lifetime value calculation
    - Test order count and average order value
    - _Requirements: 24.1-24.3_

- [x] 28. Implement order cancellation with stock restoration
  - [x] 28.1 Implement cancel order logic
    - Create cancelOrder() method in OrderManagementService
    - Transition status to CANCELLED
    - Require cancellation reason
    - Restore stock for PACKED, DISPATCHED, DELIVERED orders
    - Create status history with reason
    - _Requirements: 27.1, 27.2, 27.3, 27.4_

  - [x] 28.2 Write unit tests for order cancellation
    - Test cancellation with stock restoration
    - Test cancellation reason requirement
    - Test status history creation
    - _Requirements: 27.1-27.4_

- [x] 29. Checkpoint - Ensure all remaining features are complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 30. Implement multi-tenant data isolation verification
  - [x] 30.1 Write property test for multi-tenant data isolation
    - **Property 9: Multi-Tenant Data Isolation**
    - **Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5**
    - Generate requests with different tenant contexts
    - Assert: TenantContext correctly set from JWT
    - Assert: RoutingDataSource routes to correct tenant database
    - Assert: no cross-tenant data leakage

  - [x] 30.2 Write integration test for tenant isolation
    - Create orders as Tenant 1
    - Attempt to access Tenant 1 orders as Tenant 2
    - Assert: Tenant 2 cannot see Tenant 1 data
    - _Requirements: 17.1-17.5_

- [x] 31. Implement performance optimizations
  - [x] 31.1 Add database indexes
    - Create indexes on Order.orderNumber (unique)
    - Create indexes on Order.orderDate, Order.status
    - Create indexes on Customer.phone
    - Create indexes on Product.sku (unique)
    - Create indexes on OrderItem.productId
    - Create composite indexes for common query patterns
    - _Requirements: 30.1, 30.2, 30.3, 30.4, 30.5_

  - [x] 31.2 Optimize query performance
    - Use JOIN FETCH for eagerly loading relationships
    - Implement pagination for large result sets
    - Use query caching for frequently accessed data
    - Optimize report queries with proper aggregations
    - _Requirements: 30.1, 30.2, 30.3, 30.4_

  - [x] 31.3 Write performance tests
    - Test API response times (<200ms for CRUD)
    - Test report generation times (<2s for daily reports)
    - Test label generation times (<500ms for single label)
    - Test concurrent user handling (50 users)
    - _Requirements: 30.1, 30.2, 30.3, 30.4_

- [x] 32. Implement order number uniqueness enforcement
  - [x] 32.1 Write property test for order number uniqueness
    - **Property 6: Order Number Uniqueness**
    - **Validates: Requirements 2.1, 2.2, 2.3**
    - Generate multiple orders concurrently
    - Assert: all order numbers are unique
    - Assert: order numbers match format ORD-YYYYMMDD-XXXX

- [x] 33. Implement audit trail completeness verification
  - [x] 33.1 Write property test for audit trail completeness
    - **Property 7: Audit Trail Completeness**
    - **Validates: Requirements 6.1, 6.2, 6.3**
    - Perform various order status changes
    - Assert: each change has corresponding OrderStatusHistory record
    - Assert: history includes all required fields (previousStatus, newStatus, user, timestamp)

- [x] 34. Final integration and system testing
  - [x] 34.1 Write end-to-end integration tests
    - Test complete order lifecycle from creation to delivery
    - Test WhatsApp order processing with parsing
    - Test payment recording and tracking
    - Test stock management throughout order lifecycle
    - Test report generation with real data
    - Test dispatch label generation
    - Test Vyapar export
    - Test multi-tenant isolation

  - [x] 34.2 Perform system testing and bug fixes
    - Run all unit tests
    - Run all property-based tests
    - Run all integration tests
    - Fix any identified bugs
    - Verify all requirements are met

- [x] 35. Final checkpoint - System ready for deployment
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- **Tasks marked with `*` are optional** and can be skipped for faster MVP development
- **Property-based tests** validate universal correctness properties defined in the design document
- **Unit tests** validate specific examples and edge cases
- **Integration tests** validate end-to-end flows with real database and full Spring context
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation throughout implementation
- Implementation uses **Java Spring Boot** as specified in the design document
- Multi-tenant architecture requires careful attention to TenantContext management
- All database operations must be aware of tenant routing
- Authentication and authorization are critical for security
- Stock management requires transactional consistency
- WhatsApp parsing is a key differentiator requiring robust testing
- Reporting and export capabilities are essential for business operations


## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "1.4"] },
    { "id": 2, "tasks": ["3.1", "3.2", "6.1", "7.1", "11.1"] },
    { "id": 3, "tasks": ["3.3", "3.4", "6.2", "7.2"] },
    { "id": 4, "tasks": ["3.5", "3.6", "3.7", "6.3", "11.2"] },
    { "id": 5, "tasks": ["3.8", "4.1", "6.4"] },
    { "id": 6, "tasks": ["4.2", "4.3"] },
    { "id": 7, "tasks": ["8.1", "8.2", "8.3"] },
    { "id": 8, "tasks": ["8.4", "8.5"] },
    { "id": 9, "tasks": ["8.6", "8.7"] },
    { "id": 10, "tasks": ["8.8", "10.1"] },
    { "id": 11, "tasks": ["10.2", "10.3"] },
    { "id": 12, "tasks": ["12.1", "12.2"] },
    { "id": 13, "tasks": ["12.3", "12.4"] },
    { "id": 14, "tasks": ["12.5", "14.1", "14.2", "14.3", "14.4"] },
    { "id": 15, "tasks": ["14.5", "15.1", "15.2", "15.3"] },
    { "id": 16, "tasks": ["15.4", "16.1"] },
    { "id": 17, "tasks": ["16.2", "16.3"] },
    { "id": 18, "tasks": ["18.1", "18.8"] },
    { "id": 19, "tasks": ["18.2", "18.3", "18.4"] },
    { "id": 20, "tasks": ["18.5", "18.6", "18.7"] },
    { "id": 21, "tasks": ["18.9", "19.1"] },
    { "id": 22, "tasks": ["19.2", "20.1", "20.2"] },
    { "id": 23, "tasks": ["20.3", "21.1", "21.2"] },
    { "id": 24, "tasks": ["21.3", "23.1"] },
    { "id": 25, "tasks": ["23.2", "24.1"] },
    { "id": 26, "tasks": ["24.2", "25.1"] },
    { "id": 27, "tasks": ["25.2", "25.3"] },
    { "id": 28, "tasks": ["26.1", "26.2", "27.1", "27.2"] },
    { "id": 29, "tasks": ["27.3", "28.1"] },
    { "id": 30, "tasks": ["28.2", "30.1", "30.2"] },
    { "id": 31, "tasks": ["31.1", "31.2", "32.1", "33.1"] },
    { "id": 32, "tasks": ["31.3", "34.1"] },
    { "id": 33, "tasks": ["34.2"] }
  ]
}
```
