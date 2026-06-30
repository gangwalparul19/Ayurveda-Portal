# Salesperson Order View Feature

## Overview

Salespersons can view their own orders with **customer details masked** for privacy and security. Only Sales Head and Admin can see full customer details.

## Security & Privacy Rules

### For SALESPERSON Role:
- ✅ Can view **only their own orders**
- ❌ Cannot view other salespersons' orders
- 🔒 Customer phone: **Masked** as `XXX-XXX-1234` (last 4 digits visible)
- 🔒 Customer address: **Hidden** - Shows `[Hidden - Contact Sales Head]`
- 🔒 Customer email: **Partially masked** as `cust****@email.com`
- ✅ Can see: Order details, items, commission earned

### For MANAGER (Sales Head) Role:
- ✅ Can view **all salespersons' orders**
- ✅ **Full customer details** visible (no masking)
- ✅ Can access any order by ID

### For TENANT_ADMIN/SUPER_ADMIN Role:
- ✅ Can view **all orders**
- ✅ **Full customer details** visible
- ✅ Complete system access

## API Endpoints

### 1. Get My Orders (Salesperson View)
```
GET /api/salesperson-dashboard/{salespersonId}/my-orders?page=0&size=20
Authorization: Bearer {salesperson_token}
```

**Access**: SALESPERSON, MANAGER, TENANT_ADMIN

**Response** (for SALESPERSON):
```json
{
  "content": [
    {
      "id": 123,
      "orderNumber": "ORD-20260629-001",
      "orderDate": "2026-06-29",
      "status": "DELIVERED",
      "paymentStatus": "PAID",
      "customerId": 45,
      "customerName": "Rajesh Kumar",
      "customerPhone": "XXX-XXX-4567",  // MASKED
      "customerAddress": "[Hidden - Contact Sales Head]",  // HIDDEN
      "customerEmail": "raje****@gmail.com",  // PARTIALLY MASKED
      "subtotal": 2500.00,
      "taxAmount": 450.00,
      "shippingCharge": 50.00,
      "totalAmount": 3000.00,
      "discountAmount": 0.00,
      "itemCount": 3,
      "items": [
        {
          "productName": "Ashwagandha Capsules",
          "productSku": "SHIFA-001",
          "quantity": 2,
          "price": 500.00,
          "subtotal": 1000.00
        }
      ],
      "commissionEarned": 75.00,  // 2.5% of 3000
      "notes": "Customer prefers morning delivery"
    }
  ],
  "totalElements": 15,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

**Response** (for MANAGER/ADMIN - Full Details):
```json
{
  "content": [
    {
      "customerPhone": "9876543210",  // FULL PHONE
      "customerAddress": "123 Main St, Mumbai 400001",  // FULL ADDRESS
      "customerEmail": "rajesh@gmail.com",  // FULL EMAIL
      // ... rest same
    }
  ]
}
```

### 2. Get Order Detail
```
GET /api/salesperson-dashboard/orders/{orderId}
Authorization: Bearer {salesperson_token}
```

**Access**: SALESPERSON (own orders only), MANAGER/ADMIN (any order)

**Behavior**:
- Salesperson trying to access another's order → `403 Forbidden`
- Sales Head/Admin can access any order → Full details returned

### 3. Get All Salesperson Orders (Sales Head Only)
```
GET /api/salesperson-dashboard/all-orders?page=0&size=20
Authorization: Bearer {sales_head_token}
```

**Access**: MANAGER, TENANT_ADMIN only

Returns all orders from all salespersons with **full customer details**.

## Testing

### Test as Salesperson (sp001)

1. **Login**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sp001","password":"admin123"}'
```

2. **Get my orders** (will see masked customer details):
```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/1/my-orders?page=0&size=10" \
  -H "Authorization: Bearer {sp001_token}"
```

3. **Try to access another salesperson's order** (should fail):
```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/orders/456" \
  -H "Authorization: Bearer {sp001_token}"
# Response: 403 Forbidden - "You can only view your own orders"
```

### Test as Sales Head

1. **Login**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"saleshead","password":"admin123"}'
```

2. **View all orders** (full details):
```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/all-orders?page=0&size=20" \
  -H "Authorization: Bearer {saleshead_token}"
```

3. **View specific order** (any salesperson's):
```bash
curl -X GET "http://localhost:8080/api/salesperson-dashboard/orders/123" \
  -H "Authorization: Bearer {saleshead_token}"
```

## Implementation Details

### Masking Logic

In `SalespersonOrderResponse.maskCustomerDetails()`:

```java
// Phone: 9876543210 → XXX-XXX-3210
if (phone.length() > 4) {
    String lastFour = phone.substring(phone.length() - 4);
    maskedPhone = "XXX-XXX-" + lastFour;
}

// Email: customer@gmail.com → cust****@gmail.com
String[] parts = email.split("@");
if (parts[0].length() > 4) {
    maskedEmail = parts[0].substring(0, 4) + "****@" + parts[1];
}

// Address: Completely hidden
maskedAddress = "[Hidden - Contact Sales Head]";
```

### Access Control

1. **In Service Layer** (`SalespersonOrderService`):
   - Checks user role from authentication principal
   - Validates salesperson can only access their own orders
   - Throws `AccessDeniedException` for unauthorized access

2. **In Controller** (`@PreAuthorize`):
   - Method-level security annotations
   - Spring Security validates before method execution

### Database Query Optimization

- Uses `@EntityGraph` to eagerly fetch related entities
- Avoids N+1 query problems with items and customers
- Pagination for large result sets

## Security Considerations

### Why Mask Customer Details?

1. **Privacy Protection**: Customers' personal information is sensitive
2. **Prevents Misuse**: Salespersons shouldn't contact customers directly outside the system
3. **Audit Trail**: All customer interactions should go through proper channels
4. **Competition**: Prevents salespersons from poaching customers if they leave

### What Salesperson CAN See:

✅ Order number, date, status
✅ Product details (what was ordered)
✅ Order value and commission earned
✅ Customer name (for identification)
✅ Last 4 digits of phone (for verification if customer calls)

### What Salesperson CANNOT See:

❌ Full phone number
❌ Full address
❌ Full email address
❌ Other salespersons' orders

## Frontend Integration

### Display Masked Data

```typescript
// In Angular component
if (order.customerPhone.startsWith('XXX-XXX')) {
  // Show masked phone with lock icon
  displayPhone = '🔒 ' + order.customerPhone;
}

if (order.customerAddress.includes('[Hidden')) {
  // Show lock icon and message
  displayAddress = '🔒 Contact Sales Head for details';
}
```

### UI Indicators

- 🔒 Lock icon next to masked fields
- Tooltip: "Full details available to Sales Head only"
- Different styling for masked vs. full data

## Future Enhancements

1. **Audit Logging**: Track when salespersons view orders
2. **Time-based Unmasking**: Allow temporary access with approval
3. **Partial Unmasking**: Show full phone for verified delivery
4. **Granular Permissions**: Custom masking rules per salesperson level

## Migration from Existing Orders

If you have existing orders, assign them to salespersons:

```sql
-- Assign orders to salespersons based on date or other criteria
UPDATE orders 
SET salesperson_id = 1  -- Priya (sp001)
WHERE order_date BETWEEN '2026-06-01' AND '2026-06-10';

UPDATE orders 
SET salesperson_id = 2  -- Amit (sp002)
WHERE order_date BETWEEN '2026-06-11' AND '2026-06-20';
```

## Error Messages

| Scenario | Status Code | Message |
|----------|-------------|---------|
| Salesperson accessing another's order | 403 | "You can only view your own orders" |
| Non-salesperson trying to view orders | 403 | "Insufficient permissions to view orders" |
| Invalid order ID | 404 | "Order not found: {id}" |
| Non-Manager accessing all orders | 403 | "Only Sales Head or Admin can view all orders" |

## Deployment Checklist

- [ ] Run database migration for `salesperson_id` in orders (if not exists)
- [ ] Assign existing orders to salespersons
- [ ] Update frontend to handle masked fields
- [ ] Test all three roles: SALESPERSON, MANAGER, ADMIN
- [ ] Verify access control works correctly
- [ ] Document for sales team

## Files Created/Modified

**New Files:**
1. `SalespersonOrderResponse.java` - DTO with masking support
2. `SalespersonOrderService.java` - Business logic
3. `SALESPERSON_ORDER_VIEW_FEATURE.md` - This documentation

**Modified Files:**
1. `SalespersonDashboardController.java` - Added order endpoints
2. `OrderRepository.java` - Added query methods
3. `SALES_TEAM_SEED_DATA.sql` - Updated with sample orders

## Support

For questions or issues:
1. Check logs for `AccessDeniedException`
2. Verify user role in JWT token
3. Ensure salesperson record exists for user
4. Check order has correct `salesperson_id`
