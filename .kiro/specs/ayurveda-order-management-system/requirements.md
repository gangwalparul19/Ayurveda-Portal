# Requirements Document: Ayurveda Order & Dispatch Management System

## Introduction

The Ayurveda Order & Dispatch Management System is a configuration-based application that enables Ayurvedic product vendors to manage the complete order lifecycle from order entry through dispatch and billing. The system supports multiple order creation channels (manual entry, WhatsApp parsing, public storefront), comprehensive product and stock management, role-based user access, and advanced reporting capabilities. The application is deployed per client with configuration files defining company-specific settings, branding, and business rules. This document specifies the functional and non-functional requirements for the system.

## Glossary

- **System**: The Ayurveda Order & Dispatch Management System
- **Client**: A vendor organization using the application instance with dedicated deployment
- **Configuration**: Application settings file containing company details, branding, business rules
- **Application_Database**: Database storing user accounts and system configuration
- **Business_Database**: Database containing orders, products, customers, and operational data
- **Order**: A record representing customer purchase with line items, payments, and status
- **Order_Item**: Individual product line within an order with quantity and pricing
- **Product**: An Ayurvedic product in the catalog with SKU, pricing, and stock information
- **Customer**: Individual or business making purchases through the system
- **Salesperson**: User role responsible for creating orders and managing customer relationships
- **Payment_Record**: Individual payment transaction against an order
- **Order_Status**: Current state of order in workflow (NEW, CONFIRMED, PAID, PACKED, DISPATCHED, DELIVERED, CANCELLED, RETURNED)
- **Payment_Status**: Payment completion state (PENDING, PARTIAL, PAID, REFUNDED)
- **WhatsApp_Parser**: Service component that extracts order details from WhatsApp message text
- **Dispatch_Label**: PDF document containing shipping address and order details
- **Stock_Operation**: Type of inventory change (STOCK_IN, STOCK_OUT, ADJUSTMENT, RETURN)
- **Vyapar**: Third-party billing software used for GST invoicing
- **JWT**: JSON Web Token used for authentication

## Requirements

### Requirement 1: Order Creation from Multiple Sources

**User Story:** As a user, I want to create orders through manual entry, WhatsApp messages, and public storefront, so that I can capture orders from multiple channels.

#### Acceptance Criteria

1. WHEN an order is created THEN THE System SHALL validate that at least one Order_Item is present
2. WHEN an order is created THEN THE System SHALL validate that the specified Customer exists in Business_Database
3. WHEN an order is created with valid data THEN THE System SHALL store the original source (MANUAL, WHATSAPP, STOREFRONT, API)

### Requirement 2: Order Number Generation and Uniqueness

**User Story:** As a system administrator, I want each order to have a unique, formatted order number, so that orders can be tracked and identified consistently.

#### Acceptance Criteria

1. WHEN an order is created THEN THE System SHALL generate an order number in format ORD-YYYYMMDD-XXXX
2. THE System SHALL ensure that no two orders have the same order number
3. WHEN multiple orders are created on the same date THEN THE System SHALL increment the sequence number (XXXX) starting from 0001

### Requirement 3: WhatsApp Message Parsing

**User Story:** As a salesperson, I want to paste WhatsApp messages to automatically extract order details, so that I can quickly create orders without manual data entry.

#### Acceptance Criteria

1. WHEN WhatsApp message text is provided THEN THE WhatsApp_Parser SHALL extract customer name, phone number, and address using regex patterns
2. WHEN WhatsApp message text contains product identifiers THEN THE WhatsApp_Parser SHALL perform fuzzy matching against the product catalog
3. WHEN WhatsApp message text contains payment information THEN THE WhatsApp_Parser SHALL detect payment mode (COD, UPI, BANK_TRANSFER, ONLINE, CREDIT)
4. WHEN parsing is complete THEN THE WhatsApp_Parser SHALL return a confidence score between 0.0 and 1.0
5. WHEN parsing encounters ambiguities or missing data THEN THE WhatsApp_Parser SHALL include warning messages in the response
6. WHEN the confidence score is below 0.7 THEN THE System SHALL display a review screen for user verification

### Requirement 4: Order Total Calculation

**User Story:** As an accountant, I want order totals to be automatically calculated from line items and adjustments, so that pricing is accurate and consistent.

#### Acceptance Criteria

1. WHEN order items are added or modified THEN THE System SHALL calculate subtotal as the sum of all Order_Item line totals
2. THE System SHALL calculate total amount as subtotal minus discount amount plus tax amount plus shipping charge
3. WHEN an Order_Item is created THEN THE System SHALL calculate line total as (quantity × unit price) minus item discount plus item tax
4. THE System SHALL maintain precision of 2 decimal places for all monetary calculations
5. WHEN an order has no items THEN THE System SHALL set subtotal to zero

### Requirement 5: Order Status Workflow

**User Story:** As a user, I want orders to transition through defined statuses, so that the order lifecycle is tracked and controlled.

#### Acceptance Criteria

1. WHEN an order is created THEN THE System SHALL set initial status to NEW
2. WHEN order status is NEW THEN THE System SHALL allow transition to CONFIRMED or CANCELLED
3. WHEN order status is CONFIRMED THEN THE System SHALL allow transition to PAID or CANCELLED
4. WHEN order status is PAID THEN THE System SHALL allow transition to PACKED or CANCELLED
5. WHEN order status is PACKED THEN THE System SHALL allow transition to DISPATCHED or back to PAID
6. WHEN order status is DISPATCHED THEN THE System SHALL allow transition to DELIVERED or RETURNED
7. WHEN order status is DELIVERED THEN THE System SHALL allow transition to RETURNED
8. WHEN an invalid status transition is attempted THEN THE System SHALL reject the transition and return an error message
9. WHEN order status transitions to DISPATCHED THEN THE System SHALL require that payment status is PAID
10. WHEN order status transitions to DISPATCHED THEN THE System SHALL set the dispatched_at timestamp
11. WHEN order status transitions to DELIVERED THEN THE System SHALL set the delivered_at timestamp

### Requirement 6: Order Status Audit Trail

**User Story:** As an administrator, I want to track all order status changes with timestamps and user information, so that I can audit order history.

#### Acceptance Criteria

1. WHEN order status is changed THEN THE System SHALL create an OrderStatusHistory record
2. THE OrderStatusHistory record SHALL include previous status, new status, timestamp, user ID, and optional notes
3. WHEN retrieving order details THEN THE System SHALL include the complete status history

### Requirement 7: Payment Recording and Tracking

**User Story:** As an accountant, I want to record multiple payments against an order, so that I can track partial payments and payment history.

#### Acceptance Criteria

1. WHEN a payment is recorded THEN THE System SHALL create a Payment_Record with amount, payment mode, transaction reference, and timestamp
2. THE System SHALL validate that payment amount is positive and does not exceed remaining order balance
3. THE System SHALL calculate total paid as the sum of all Payment_Record amounts for an order
4. WHEN total paid equals order total THEN THE System SHALL set payment status to PAID
5. WHEN total paid is greater than zero but less than order total THEN THE System SHALL set payment status to PARTIAL
6. WHEN total paid is zero THEN THE System SHALL set payment status to PENDING
7. WHEN a payment would exceed order total THEN THE System SHALL reject the payment and return an error

### Requirement 8: Product Management

**User Story:** As a manager, I want to create and manage products with SKU, pricing, and categories, so that I can maintain an accurate product catalog.

#### Acceptance Criteria

1. WHEN a product is created THEN THE System SHALL require SKU, name, sale price, and MRP
2. THE System SHALL ensure that SKU is unique within the Business_Database
3. WHEN a product is created THEN THE System SHALL allow optional fields for category, description, and weight
4. WHEN a product is updated THEN THE System SHALL maintain the product ID and creation timestamp
5. WHEN a product is deleted THEN THE System SHALL prevent deletion if the product appears in any orders

### Requirement 9: Stock Management and History

**User Story:** As a warehouse manager, I want to track stock levels and movements, so that I can manage inventory effectively.

#### Acceptance Criteria

1. WHEN a stock update occurs THEN THE System SHALL create a StockHistory record with operation type, quantity changed, and timestamp
2. WHEN order status transitions to PACKED THEN THE System SHALL reduce stock quantity for all Order_Items
3. WHEN order status transitions to CANCELLED or RETURNED from PACKED or later status THEN THE System SHALL restore stock quantities
4. THE System SHALL prevent stock quantity from becoming negative
5. WHEN stock quantity falls below a threshold THEN THE System SHALL include the product in low stock queries
6. THE StockHistory record SHALL include quantity before, quantity changed, quantity after, and reference to the order or adjustment

### Requirement 10: Customer Management

**User Story:** As a salesperson, I want to manage customer information and find customers by phone, so that I can quickly reuse customer data.

#### Acceptance Criteria

1. WHEN a customer is created THEN THE System SHALL require name and phone number
2. WHEN a customer is created THEN THE System SHALL allow optional address, city, state, pincode, and email
3. WHEN searching for a customer by phone THEN THE System SHALL return matching customer or null
4. WHEN a WhatsApp order is parsed THEN THE System SHALL find existing customer by phone or create new customer if not found
5. THE System SHALL detect potential duplicate customers based on phone number

### Requirement 11: Duplicate Order Detection

**User Story:** As a salesperson, I want to be warned about potential duplicate orders, so that I can avoid creating duplicate entries.

#### Acceptance Criteria

1. WHEN checking for duplicates THEN THE System SHALL search for orders from the same customer within 7 days of the order date
2. WHEN comparing orders THEN THE System SHALL calculate product overlap using Jaccard similarity
3. WHEN product overlap is 80% or greater THEN THE System SHALL flag the order as a potential duplicate
4. THE System SHALL return a list of potential duplicate orders with similarity scores
5. WHEN no matching customer exists THEN THE System SHALL indicate no duplicates found

### Requirement 12: Dispatch Label Generation

**User Story:** As a dispatcher, I want to generate PDF dispatch labels for orders, so that I can print shipping labels for courier pickup.

#### Acceptance Criteria

1. WHEN generating a dispatch label THEN THE System SHALL require that order status is PAID or later
2. THE dispatch label SHALL include order number, order date, customer name, shipping address, city, state, pincode, and phone
3. THE dispatch label SHALL include a list of products with quantities
4. THE dispatch label SHALL include a barcode encoding the order number in Code128 format
5. THE dispatch label SHALL include vendor name, address, and phone
6. WHEN generating bulk labels THEN THE System SHALL create a single PDF containing multiple labels
7. THE generated PDF SHALL be less than 500KB per label

### Requirement 13: Sales and Collection Reports

**User Story:** As a manager, I want to view daily and monthly sales reports, so that I can track business performance.

#### Acceptance Criteria

1. WHEN requesting a daily sales report THEN THE System SHALL aggregate orders by order date for the specified date
2. WHEN requesting a monthly sales report THEN THE System SHALL aggregate orders by order date for the specified month
3. THE sales report SHALL include total orders, total sales amount, delivered orders, and cancelled orders
4. THE sales report SHALL only count orders with status DELIVERED in sales amount
5. WHEN requesting a salesperson sales report THEN THE System SHALL filter orders by salesperson and date range

### Requirement 14: Product-Wise and Geographic Reports

**User Story:** As a manager, I want to view product-wise and state-wise sales analysis, so that I can identify top-selling products and geographic trends.

#### Acceptance Criteria

1. WHEN requesting a product-wise report THEN THE System SHALL aggregate sales by product for the date range
2. THE product-wise report SHALL include product name, total quantity sold, and total sales amount
3. WHEN requesting a state-wise report THEN THE System SHALL aggregate sales by customer state
4. THE state-wise report SHALL include state name, order count, and total sales amount
5. WHEN requesting top-selling products THEN THE System SHALL order products by quantity sold descending and limit results

### Requirement 15: Report Export

**User Story:** As an accountant, I want to export reports in CSV, Excel, and PDF formats, so that I can share and analyze data in external tools.

#### Acceptance Criteria

1. WHEN exporting a report to CSV THEN THE System SHALL format data as comma-separated values with headers
2. WHEN exporting a report to Excel THEN THE System SHALL create an XLSX file with proper formatting
3. WHEN exporting a report to PDF THEN THE System SHALL create a formatted PDF document
4. THE System SHALL set appropriate content-type headers for each export format

### Requirement 16: Vyapar Billing Export

**User Story:** As an accountant, I want to export orders in Vyapar-compatible format, so that I can import them into Vyapar for GST invoicing.

#### Acceptance Criteria

1. WHEN exporting to Vyapar format THEN THE System SHALL map order data to Vyapar CSV format
2. THE Vyapar export SHALL include customer name, address, product details, quantities, prices, and totals
3. WHEN exporting daily orders THEN THE System SHALL include all orders for the specified date
4. WHEN exporting monthly orders THEN THE System SHALL include all orders for the specified month

### Requirement 17: Configuration Management

**User Story:** As a system administrator, I want to configure company settings through configuration files, so that the application can be customized for different clients without code changes.

#### Acceptance Criteria

1. THE System SHALL load company name, address, phone, email, and logo from configuration file
2. THE System SHALL load business rules (low stock threshold, order number prefix, tax rates) from configuration
3. WHEN configuration is updated THEN THE System SHALL apply new settings on application restart
4. THE configuration file SHALL be stored in application.yml or application.properties
5. THE System SHALL validate configuration on startup and fail fast if invalid

### Requirement 18: User Authentication and Authorization

**User Story:** As a user, I want to authenticate with username and password and receive a JWT token, so that I can securely access the system.

#### Acceptance Criteria

1. WHEN a user provides valid credentials THEN THE System SHALL generate a JWT token with user ID, role, and permissions
2. THE JWT access token SHALL be valid for 24 hours
3. THE JWT refresh token SHALL be valid for 7 days
4. WHEN a request is received THEN THE System SHALL validate the JWT token signature and expiration
5. WHEN JWT validation fails THEN THE System SHALL return HTTP 401 Unauthorized

### Requirement 19: Role-Based Access Control

**User Story:** As an administrator, I want to control access to features based on user roles, so that users only access appropriate functionality.

#### Acceptance Criteria

1. THE System SHALL support roles: ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT
2. WHEN a user with SALESPERSON role attempts an operation THEN THE System SHALL allow order creation and status updates but restrict user management
3. WHEN a user with DISPATCHER role attempts an operation THEN THE System SHALL allow viewing PAID/PACKED orders and generating dispatch labels
4. WHEN a user with ACCOUNTANT role attempts an operation THEN THE System SHALL allow payment recording and report generation
5. WHEN a user attempts an unauthorized operation THEN THE System SHALL return HTTP 403 Forbidden

### Requirement 20: Salesperson Management

**User Story:** As an administrator, I want to manage salesperson information and commission rates, so that I can track sales team performance.

#### Acceptance Criteria

1. WHEN a salesperson is created THEN THE System SHALL require employee code, name, and linked user account
2. THE System SHALL ensure employee code is unique within Business_Database
3. WHEN a salesperson is created THEN THE System SHALL allow optional commission rate (0-100 percent)
4. THE System SHALL support salesperson status: ACTIVE, INACTIVE, ON_LEAVE
5. WHEN filtering orders by salesperson THEN THE System SHALL return only orders assigned to that salesperson

### Requirement 21: Stock History Tracking

**User Story:** As an inventory manager, I want to view complete stock movement history for products, so that I can audit inventory changes.

#### Acceptance Criteria

1. WHEN stock is updated THEN THE System SHALL record quantity before, quantity changed, quantity after, operation type, and user ID
2. THE StockHistory SHALL include reference type (ORDER, PURCHASE, ADJUSTMENT) and reference ID
3. WHEN querying stock history THEN THE System SHALL return records filtered by product and date range
4. THE System SHALL preserve stock history records even when orders are deleted

### Requirement 22: Order Filtering and Search

**User Story:** As a user, I want to filter and search orders by status, date range, salesperson, and customer, so that I can quickly find relevant orders.

#### Acceptance Criteria

1. WHEN requesting orders with filters THEN THE System SHALL support pagination with page number and page size
2. THE System SHALL support filtering by order status (single or multiple statuses)
3. THE System SHALL support filtering by date range (from date and to date)
4. THE System SHALL support filtering by salesperson ID
5. THE System SHALL support searching by customer name or phone number
6. THE System SHALL support searching by order number

### Requirement 23: Bulk Order Operations

**User Story:** As a dispatcher, I want to update status for multiple orders at once, so that I can efficiently process batches of orders.

#### Acceptance Criteria

1. WHEN bulk updating order status THEN THE System SHALL accept a list of order IDs and target status
2. THE System SHALL validate status transition rules for each order individually
3. WHEN any order fails validation THEN THE System SHALL continue processing remaining orders and return partial success
4. THE System SHALL create status history records for all successfully updated orders

### Requirement 24: Customer Order History

**User Story:** As a salesperson, I want to view complete order history for a customer, so that I can understand their purchase patterns.

#### Acceptance Criteria

1. WHEN requesting customer order history THEN THE System SHALL return all orders for the customer ordered by date descending
2. THE customer order history SHALL include order count, total amount spent, and average order value
3. THE System SHALL calculate customer lifetime value as sum of all DELIVERED order totals

### Requirement 25: Error Handling for Insufficient Stock

**User Story:** As a user, I want to be notified when attempting to pack an order with insufficient stock, so that I can take corrective action.

#### Acceptance Criteria

1. WHEN transitioning order to PACKED with insufficient stock THEN THE System SHALL return HTTP 409 Conflict
2. THE error message SHALL include product name, available stock, and required quantity
3. THE System SHALL roll back the transaction and make no partial changes
4. THE order status SHALL remain unchanged after the error

### Requirement 26: Product Fuzzy Matching

**User Story:** As a system processing WhatsApp orders, I want to match product names using fuzzy matching, so that variations in product names can be recognized.

#### Acceptance Criteria

1. WHEN matching a product identifier THEN THE System SHALL first attempt exact SKU match
2. WHEN exact SKU match fails THEN THE System SHALL calculate similarity scores using Levenshtein distance for product names and SKUs
3. THE System SHALL select the product with highest similarity score above 0.6 threshold
4. WHEN no product meets the threshold THEN THE System SHALL return null
5. THE System SHALL return match confidence score as part of parsed product line

### Requirement 27: Order Cancellation

**User Story:** As a manager, I want to cancel orders with a reason, so that cancelled orders are tracked and stock is restored if necessary.

#### Acceptance Criteria

1. WHEN an order is cancelled THEN THE System SHALL transition status to CANCELLED
2. THE System SHALL require a cancellation reason in notes
3. WHEN cancelling a PACKED, DISPATCHED, or DELIVERED order THEN THE System SHALL restore stock quantities for all Order_Items
4. THE System SHALL create status history record with cancellation reason and user ID

### Requirement 28: Return Processing

**User Story:** As an accountant, I want to process order returns, so that returned orders are tracked and stock is restored.

#### Acceptance Criteria

1. WHEN an order is returned THEN THE System SHALL transition status to RETURNED
2. THE System SHALL restore stock quantities for all Order_Items
3. WHEN return is from DELIVERED status THEN THE System SHALL allow recording return reason
4. THE System SHALL create status history record with return information

### Requirement 29: Invoice Generation

**User Story:** As an accountant, I want to generate invoice PDFs for orders, so that I can provide formal invoices to customers.

#### Acceptance Criteria

1. WHEN generating an invoice THEN THE System SHALL include vendor details, customer details, order items, and totals
2. THE invoice SHALL include tax calculations and breakdowns
3. THE invoice SHALL be formatted as a PDF document
4. THE System SHALL allow invoice generation for orders with status PAID or later

### Requirement 30: System Performance Requirements

**User Story:** As a user, I want the system to respond quickly to my actions, so that I can work efficiently.

#### Acceptance Criteria

1. THE System SHALL respond to CRUD API operations within 200 milliseconds at 95th percentile
2. WHEN generating a daily report THEN THE System SHALL complete within 2 seconds
3. WHEN generating a single dispatch label THEN THE System SHALL complete within 500 milliseconds
4. THE System SHALL support 50 concurrent users per tenant without performance degradation
5. WHEN executing indexed database queries THEN THE System SHALL complete within 100 milliseconds

### Requirement 31: Data Validation

**User Story:** As a developer, I want all input data to be validated against business rules, so that data integrity is maintained.

#### Acceptance Criteria

1. WHEN validating phone numbers THEN THE System SHALL accept Indian format (10 digits with optional +91/0 prefix)
2. WHEN validating email addresses THEN THE System SHALL enforce RFC 5322 format
3. WHEN validating monetary amounts THEN THE System SHALL ensure non-negative values
4. WHEN validating dates THEN THE System SHALL ensure order date is not in future
5. THE System SHALL sanitize HTML content in text fields to prevent XSS attacks

### Requirement 32: Audit Logging

**User Story:** As a security administrator, I want all significant actions to be logged, so that I can audit system usage and investigate issues.

#### Acceptance Criteria

1. WHEN a user logs in THEN THE System SHALL log timestamp, user ID, and IP address
2. WHEN login fails THEN THE System SHALL log failed attempt with username and IP address
3. WHEN payment is recorded THEN THE System SHALL log user ID and timestamp in Payment_Record
4. WHEN order status changes THEN THE System SHALL log change in OrderStatusHistory with user ID
5. THE System SHALL provide API access logs with user, endpoint, and timestamp

