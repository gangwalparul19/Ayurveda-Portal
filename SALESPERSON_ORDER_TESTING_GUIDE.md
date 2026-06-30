# Salesperson Order View - Testing & Verification Guide

## Quick Status Check

### ✅ What's Implemented
1. **OrderResponse.java** - Fixed: Customer masking logic complete
2. **OrderController.getAllOrders()** - Filters by salesperson role automatically
3. **OrderService.getOrdersAsResponse()** - Supports salespersonId filtering
4. **OrderRepository.findAllBySalespersonId()** - Database query ready
5. **SalespersonRepository.findByPlatformUserId()** - User lookup ready
6. **Test Data** - 5 salespersons seeded with orders and targets

### 📋 Verification Checklist
- [ ] Backend code compiles without errors
- [ ] Salesperson login works (sp001-sp005, password: admin123)
- [ ] Salesperson sees only their own orders
- [ ] Customer phone masked as XXX-XXX-XXXX (last 4 digits visible)
- [ ] Customer email masked as cust****@email.com
- [ ] Customer address/city/state show "[Hidden]"
- [ ] Customer name is visible (NOT masked)
- [ ] Sales Head sees all orders with full details
- [ ] Access control prevents unauthorized viewing
- [ ] Frontend displays masked data correctly

---

## Local Testing (Step-by-Step)

### Step 1: Run the Backend

1. Start your Java backend:
```bash
cd backend
mvn spring-boot:run
```

2. Verify it starts on `http://localhost:8080`

### Step 2: Login as Salesperson

Use any REST client (Postman, curl, Thunderclient, etc.):

**Endpoint**: `POST http://localhost:8080/api/auth/login`

**Test User 1: Priya (sp001)**
```json
{
  "username": "sp001",
  "password": "admin123"
}
```

**Test User 2: Amit (sp002)**
```json
{
  "username": "sp002",
  "password": "admin123"
}
```

**Test User 3: Sales Head**
```json
{
  "username": "saleshead",
  "password": "admin123"
}
```

**Expected Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "username": "sp001",
  "role": "SALESPERSON"
}
```

Save the token for next steps.

### Step 3: Test Salesperson Order Filtering

**Endpoint**: `GET http://localhost:8080/api/orders?page=0&size=20`

**Headers**:
```
Authorization: Bearer {token_from_step2}
Content-Type: application/json
```

**Expected Results**:

✅ **For sp001 (Priya)**: Should see orders created by Priya only
✅ **For sp002 (Amit)**: Should see orders created by Amit only (different from Priya)
✅ **For saleshead**: Should see ALL orders from all salespersons

**Exact Assertion**:
```
sp001 total orders: X
sp002 total orders: Y
(where X ≠ Y for test data to be valid)
```

### Step 4: Verify Data Masking

When logged in as **salesperson**, check the response:

```json
{
  "id": 123,
  "orderNumber": "ORD-20260629-001",
  "customer": {
    "id": 45,
    "name": "Rajesh Kumar",              // ✅ VISIBLE
    "phone": "XXX-XXX-4567",             // ✅ MASKED (last 4 digits)
    "email": "raje****@gmail.com",       // ✅ PARTIALLY MASKED
    "city": "[Hidden]",                  // ✅ HIDDEN
    "state": "[Hidden]"                  // ✅ HIDDEN
  },
  "salesperson": {
    "id": 1,
    "name": "Priya Sharma"
  }
}
```

**Verify Each Field**:

| Field | Salesperson View | Sales Head View | Rule |
|-------|------------------|-----------------|------|
| `name` | Rajesh Kumar | Rajesh Kumar | Always visible |
| `phone` | XXX-XXX-4567 | 9876543210 | Salesperson masked |
| `email` | raje****@gmail.com | rajesh@gmail.com | Salesperson masked |
| `city` | [Hidden] | Mumbai | Salesperson hidden |
| `state` | [Hidden] | Maharashtra | Salesperson hidden |

### Step 5: Test Access Control

**Test Case 1: Salesperson Access to Own Order**
```
GET http://localhost:8080/api/orders/1
Header: Authorization: Bearer {sp001_token}
```
✅ **Expected**: 200 OK - Shows masked customer details

**Test Case 2: Salesperson Access to Another's Order**
```
GET http://localhost:8080/api/orders/456  // order by sp002
Header: Authorization: Bearer {sp001_token}
```
❌ **Expected**: 403 Forbidden - "Access Denied"

**Test Case 3: Sales Head Access to Any Order**
```
GET http://localhost:8080/api/orders/456  // order by sp002
Header: Authorization: Bearer {saleshead_token}
```
✅ **Expected**: 200 OK - Shows FULL customer details (no masking)

### Step 6: Test Pagination

```
GET http://localhost:8080/api/orders?page=0&size=5
Header: Authorization: Bearer {sp001_token}
```

✅ **Expected**:
- `totalElements`: X (total orders for sp001)
- `totalPages`: Y (calculated from total/5)
- `content`: Array of 5 orders (or less if fewer available)
- `number`: 0 (current page)

---

## Testing Order Count Assertions

Use this SQL to verify test data is correct:

```sql
-- Check how many orders each salesperson has
SELECT 
    s.employee_code,
    s.name,
    COUNT(o.id) as order_count
FROM salespersons s
LEFT JOIN orders o ON s.id = o.salesperson_id
GROUP BY s.id
ORDER BY order_count DESC;

-- Sample output:
-- SP001  | Priya Sharma  | 15
-- SP002  | Amit Patel    | 12
-- SP003  | Neha Singh    | 10
-- SP004  | Vikram Reddy  | 18
-- SP005  | Anita Desai   | 8
```

---

## Frontend Testing (Angular)

### Test in Component
```typescript
// After receiving orders from API
orders.forEach(order => {
  // Verify masking is present
  expect(order.customer.phone).toMatch(/^XXX-XXX-\d{4}$/);
  expect(order.customer.email).toMatch(/^\w+\*+@\w+\.\w+$/);
  expect(order.customer.city).toBe('[Hidden]');
  expect(order.customer.state).toBe('[Hidden]');
  
  // Verify name is visible
  expect(order.customer.name).toBeTruthy();
  expect(order.customer.name).not.toBe('[Hidden]');
});
```

### Test in Template
Display orders in a table:
```html
<table>
  <tr *ngFor="let order of orders">
    <td>{{ order.orderNumber }}</td>
    <td>{{ order.customer.name }}</td>
    <td>
      🔒 {{ order.customer.phone }}
      <span title="Full details available to Sales Head only">(Masked)</span>
    </td>
    <td>🔒 {{ order.customer.email }}</td>
    <td>{{ order.customer.city }}</td>
  </tr>
</table>
```

---

## Common Issues & Debugging

### Issue 1: "Salesperson sees all 19 orders instead of just their own"

**Root Cause**: OrderController not filtering by salesperson ID

**Check**:
```bash
# In logs, you should see:
DEBUG: Salesperson sp001 (ID: 1) - filtering orders by salesperson_id=1

# If not filtering, the code path is wrong
```

**Fix Verification**:
1. Confirm OrderController.getAllOrders() has the salesperson filtering logic
2. Check that authentication.getAuthorities() contains "ROLE_SALESPERSON"
3. Verify salespersonRepository.findByPlatformUserId() returns non-null

### Issue 2: "Phone shows full number instead of XXX-XXX-XXXX"

**Root Cause**: maskSensitiveData() not being called

**Check**:
```bash
# In logs, verify:
DEBUG: Calling maskSensitiveData() for salesperson

# Also check the response:
# If phone is like "9876543210" → masking not called
# If phone is like "XXX-XXX-3210" → masking works correctly
```

**Fix Verification**:
1. Confirm OrderResponse.CustomerSummary.maskSensitiveData() exists
2. Verify OrderController calls it: `order.getCustomer().maskSensitiveData()`
3. Check that isSalesperson flag is true

### Issue 3: "403 Forbidden when salesperson tries to view any order"

**Root Cause**: @PreAuthorize annotation or access control is too restrictive

**Check**:
```bash
# In logs, look for:
WARN: Access Denied to /api/orders/1

# This means either:
# 1. Method-level @PreAuthorize is rejecting the role
# 2. Custom security checks are failing
```

**Fix Verification**:
1. Verify @PreAuthorize includes SALESPERSON: `"hasAnyRole('TENANT_ADMIN', 'MANAGER', 'SALESPERSON', 'DISPATCHER')"`
2. Check that JWT token contains correct role
3. Ensure authentication principal is set correctly

### Issue 4: "Getting java.lang.NumberFormatException when extracting platform user ID"

**Root Cause**: Username is not numeric (can happen if using email as username)

**Check**:
```bash
# In logs:
DEBUG: Platform user ID from JWT: "sp001"
WARN: Cannot parse as long

# This is expected - fall through to use parameter
```

**Fix Verification**:
1. If username is not numeric, code should handle gracefully
2. Should fall back to salespersonId parameter if provided
3. Should return empty result set if neither works

---

## Deployment Checklist

### Before Pushing to GitHub

- [ ] Run `mvn clean compile -DskipTests` - No errors
- [ ] Read OrderResponse.java - No syntax errors, all classes properly closed
- [ ] Read OrderController.java - GET /api/orders has salesperson filtering
- [ ] Verify test data: 5 salespersons + orders created
- [ ] Login as each salesperson - Works with correct role
- [ ] Check order count per salesperson - Different counts for different users
- [ ] Verify masking - Phone/email/address properly masked/hidden
- [ ] Test cross-access - sp001 cannot see sp002's orders
- [ ] Sales head test - Can see all orders with full details

### Before Deploying to Railway

1. **Backup Database**:
```bash
# Export current database
mysqldump -u user -p shifa_db > backup_$(date +%Y%m%d).sql
```

2. **Run Seed Data**:
```bash
# Execute on Railway database
mysql -h host -u user -p < SALES_TEAM_SEED_DATA.sql
```

3. **Verify Data**:
```sql
SELECT COUNT(*) FROM salespersons WHERE status = 'ACTIVE';
-- Should return: 5
```

4. **Test Production**:
```bash
curl -X POST https://ayurveda-portal-production.up.railway.app/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sp001","password":"admin123"}'
```

---

## Expected Test Data

After running SALES_TEAM_SEED_DATA.sql:

### Platform Users Created
```
┌───────────┬──────────┬────────────────┬─────────────────┐
│ Username  │ Email    │ Role           │ Full Name       │
├───────────┼──────────┼────────────────┼─────────────────┤
│ saleshead │ saleshe…│ MANAGER        │ Rajesh Kumar    │
│ sp001     │ priya@…  │ SALESPERSON    │ Priya Sharma    │
│ sp002     │ amit@…   │ SALESPERSON    │ Amit Patel      │
│ sp003     │ neha@…   │ SALESPERSON    │ Neha Singh      │
│ sp004     │ vikram@… │ SALESPERSON    │ Vikram Reddy    │
│ sp005     │ anita@…  │ SALESPERSON    │ Anita Desai     │
└───────────┴──────────┴────────────────┴─────────────────┘
```

### Salesperson Records
```
┌─────┬────────┬──────────────┬──────────────┬─────┐
│ ID  │ Code   │ Name         │ Phone        │ ... │
├─────┼────────┼──────────────┼──────────────┼─────┤
│  1  │ SP001  │ Priya Sharma │ 9876543210   │ ... │
│  2  │ SP002  │ Amit Patel   │ 9876543211   │ ... │
│  3  │ SP003  │ Neha Singh   │ 9876543212   │ ... │
│  4  │ SP004  │ Vikram Reddy │ 9876543213   │ ... │
│  5  │ SP005  │ Anita Desai  │ 9876543214   │ ... │
└─────┴────────┴──────────────┴──────────────┴─────┘
```

### Password for All Users
```
All users (salespersons + saleshead): 
Username: sp001-sp005, saleshead
Password: admin123
```

---

## Quick Command Reference

### Compile Backend
```bash
cd backend
mvn clean compile -DskipTests
```

### Run Backend
```bash
cd backend
mvn spring-boot:run
```

### Login as Salesperson
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sp001","password":"admin123"}'
```

### Get My Orders (Salesperson)
```bash
TOKEN="<paste-token-here>"
curl -X GET "http://localhost:8080/api/orders?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq
```

### Get My Orders (Sales Head - All Orders)
```bash
TOKEN="<paste-token-here>"
curl -X GET "http://localhost:8080/api/orders?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq
```

### Check Specific Order
```bash
TOKEN="<paste-token-here>"
curl -X GET "http://localhost:8080/api/orders/1" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq
```

### Count Orders by Salesperson
```sql
SELECT s.employee_code, COUNT(o.id) as order_count
FROM salespersons s
LEFT JOIN orders o ON s.id = o.salesperson_id
GROUP BY s.id
ORDER BY order_count DESC;
```

---

## Success Criteria

✅ **All tests pass when**:

1. **Authentication**: All 5 salespersons + 1 sales head can login with password `admin123`
2. **Order Filtering**: 
   - sp001 sees ~15 orders
   - sp002 sees ~12 orders  
   - sp003 sees ~10 orders
   - sp004 sees ~18 orders
   - sp005 sees ~8 orders
   - saleshead sees all ~63 orders
3. **Data Masking**: Phone/email/address masked for salesperson, full for manager
4. **Access Control**: sp001 cannot view sp002's order (403 Forbidden)
5. **Frontend**: Orders display correctly with masking indicators (🔒)

---

## Next Steps After Testing

1. ✅ Verify all local tests pass
2. ✅ Run backend compile successfully
3. ✅ Push code to GitHub (new branch, not main)
4. ✅ Deploy to Railway (backend + new seed data)
5. ✅ Test on production URL
6. ✅ Get stakeholder approval
7. ✅ Merge to main branch

---

## Questions or Issues?

Check these files:
- `OrderResponse.java` - Masking logic
- `OrderController.java` - Filter logic
- `SalespersonOrderService.java` - Business logic
- `OrderRepository.java` - Database queries
- `SALES_TEAM_SEED_DATA.sql` - Test data

