# Shifa Ayurveda — Order & Dispatch Management System

A full-stack Ayurvedic e-commerce and operations platform with a public storefront, customer accounts,
and a complete admin back-office for order lifecycle management, dispatch, billing, and reporting.

**Stack:** Java 21 · Spring Boot 3.3.6 · Angular 19 · MySQL 8 · Razorpay · JWT

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Architecture](#architecture)
3. [Database](#database)
4. [Backend API Reference](#backend-api-reference)
5. [Frontend Routes](#frontend-routes)
6. [Features](#features)
7. [Configuration](#configuration)
8. [Roles & Permissions](#roles--permissions)
9. [Payment Gateway (Razorpay)](#payment-gateway-razorpay)
10. [Email Notifications](#email-notifications)
11. [Coupon System](#coupon-system)
12. [Storefront Customer Accounts](#storefront-customer-accounts)
13. [Product Reviews](#product-reviews)
14. [File Structure](#file-structure)
15. [Pre-Launch Backlog](#pre-launch-backlog)

---

## Quick Start

### Prerequisites
- Java 21
- Maven 3.8+
- MySQL 8.x (local on port 3306)
- Node.js 18+ / npm

### 1. Database setup
```sql
CREATE DATABASE shifa_db;
```
Then run the seed script to populate initial data:
```
mysql -u root -p shifa_db < SEED_DEMO_DATA.sql
```
The backend uses `ddl-auto: update` so all tables are created automatically on first start.

### 2. Start the backend
```bash
cd backend
mvn spring-boot:run
# Starts on http://localhost:8080/api
```

### 3. Start the frontend
```bash
cd frontend
npm install   # first time only
npm start
# Opens at http://localhost:4200
```

### 4. Log in (admin)
- URL: `http://localhost:4200/login`
- Username: **admin**
- Password: **admin123**
- Role: `TENANT_ADMIN` — full access to all operational screens

### 5. Browse the store
- URL: `http://localhost:4200/store`
- No login required to browse; register at `/store/register` for an account

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│  Angular 19 Frontend  (port 4200)                │
│  Admin Panel  +  Public Storefront               │
└─────────────────────────┬────────────────────────┘
                          │ HTTP/REST + JWT
┌─────────────────────────▼────────────────────────┐
│  Spring Boot 3.3.6  (port 8080, context /api)    │
│  SecurityConfig · JWT filter · Role hierarchy    │
│  ┌────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │  Order /   │  │  Storefront  │  │ Reports/ │ │
│  │  Dispatch  │  │  Auth/Orders │  │ Billing  │ │
│  └────────────┘  └──────────────┘  └──────────┘ │
│  ┌──────────────────────────────────────────────┐│
│  │  MySQL 8 (shifa_db)  –  22 tables            ││
│  └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────┘
                          │
              ┌───────────┴────────────┐
              │  Razorpay API (India)  │
              │  Gmail SMTP (email)    │
              └────────────────────────┘
```

**Profile:** The application runs in `simple` profile — a single-client mode with one database.
The `dev` profile (multi-tenant with per-tenant DB routing) exists in the codebase but is inactive.

---

## Database

**Database:** `shifa_db`  
**Connection:** `jdbc:mysql://localhost:3306/shifa_db` (user: root, pw: root@123 — change for production)  
**Schema management:** Hibernate `ddl-auto: update` (creates/alters tables on startup; never drops)

### Tables

| Table | Purpose |
|-------|---------|
| `platform_users` | Admin/staff accounts (admin panel login) |
| `customers` | End-customer master data (name, phone, address) |
| `storefront_users` | Storefront customer accounts (email/password login) |
| `products` | Product catalog (SKU, MRP, sale price, stock, images) |
| `product_reviews` | Customer star ratings and reviews (with admin approval) |
| `orders` | Order header (status, totals, payment, Razorpay IDs, coupon) |
| `order_items` | Line items with product snapshots at order time |
| `order_status_history` | Full audit trail of every status change |
| `payment_records` | Individual payment transactions against an order |
| `stock_history` | Every stock movement (IN/OUT/ADJUSTMENT/RETURN) |
| `coupons` | Discount codes (PERCENT or FLAT, validity, usage limits) |
| `coupon_usages` | Per-customer coupon usage tracking |
| `salespersons` | Sales team members with commission rates |
| `salesperson_targets` | Monthly sales targets and achievement tracking |
| `dispatch_labels` | Generated PDF dispatch labels |
| `billing_exports` | Vyapar/CSV export history |
| `company_config` | Company name, address, GSTIN, tax rates, feature toggles |
| `roles` | Role definitions for RBAC |
| `role_permissions` | Permission assignments per role |
| `audit_log` | Security events (login, payment, order changes) |
| `tenant_ui_config` | Per-tenant branding (colors, logo — multi-tenant future use) |
| `tenants` | Tenant registry (multi-tenant future use) |

---

## Backend API Reference

Base URL: `http://localhost:8080/api`  
All admin endpoints require `Authorization: Bearer <token>` (admin JWT from `/auth/login`).  
All `/storefront/**` endpoints are public or use a separate storefront JWT.

### Authentication (Admin)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | Login; returns access + refresh JWT |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Audit-log logout |
| GET  | `/auth/me` | Current user profile |

### Storefront Auth (Customer)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/storefront/auth/register` | Register new customer account |
| POST | `/storefront/auth/login` | Login; returns storefront JWT |
| GET  | `/storefront/auth/profile` | Get profile (JWT required) |
| PUT  | `/storefront/auth/profile` | Update profile (JWT required) |
| PUT  | `/storefront/auth/change-password` | Change password (JWT required) |

### Storefront — Products & Catalog

| Method | Path | Description |
|--------|------|-------------|
| GET | `/storefront/products` | Paginated product list (supports `?category=`, `?sortBy=`, `?sortDir=`) |
| GET | `/storefront/products/{id}` | Single product detail |
| GET | `/storefront/products/featured` | Featured products (most recent) |
| GET | `/storefront/products/search?query=` | Search by name/description |
| GET | `/storefront/categories` | All distinct categories |
| POST | `/storefront/products/{id}/share` | Increment WhatsApp share count |
| POST | `/storefront/products/{id}/view` | Increment view count |
| GET  | `/storefront/config` | Branding config (company name, logo URL) |

### Storefront — Orders & Reviews

| Method | Path | Description |
|--------|------|-------------|
| POST | `/storefront/orders` | Place an order (guest checkout) |
| GET  | `/storefront/my-orders` | My order history (storefront JWT) |
| GET  | `/storefront/my-orders/{id}` | Order detail with status timeline (storefront JWT) |
| GET  | `/storefront/reviews/{productId}` | Approved reviews with average rating |
| POST | `/storefront/reviews/{productId}` | Submit a review (pending approval) |

### Storefront — Payments & Coupons

| Method | Path | Description |
|--------|------|-------------|
| POST | `/storefront/payment/create-order` | Create Razorpay order; returns `{razorpayOrderId, keyId}` |
| POST | `/storefront/payment/verify` | Verify payment signature; marks order PAID |
| POST | `/storefront/coupon/validate` | Validate coupon; returns discount amount |

### Orders (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/orders` | Paginated list (filters: status, date range, salesperson) |
| GET    | `/orders/{id}` | Order detail |
| POST   | `/orders/manual` | Create manual order |
| POST   | `/orders/whatsapp` | Create order from WhatsApp text |
| POST   | `/orders/storefront` | Create storefront order (used internally) |
| PUT    | `/orders/{id}/status` | Transition order status |
| POST   | `/orders/{id}/return` | Process return |
| DELETE | `/orders/{id}` | Cancel order |
| POST   | `/orders/bulk/status` | Bulk status update |
| POST   | `/orders/duplicate-check` | Check for duplicate orders |

### Products (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/products` | Paginated list (filters: category, search) |
| POST   | `/products` | Create product |
| PUT    | `/products/{id}` | Update product |
| DELETE | `/products/{id}` | Delete product |
| PATCH  | `/products/{id}/stock` | Manual stock adjustment |
| GET    | `/products/low-stock` | Products below threshold |
| GET    | `/products/{id}/stock-history` | Stock movement history |

### Customers (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/customers` | Paginated list |
| POST   | `/customers` | Create customer |
| PUT    | `/customers/{id}` | Update customer |
| GET    | `/customers/{id}` | Customer detail |
| GET    | `/customers/{id}/orders` | Customer order history |
| GET    | `/customers/{id}/lifetime-value` | Total spend |

### Reports (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/reports/dashboard` | KPI stats (today orders, revenue, low stock) |
| GET | `/reports/daily-sales?date=` | Daily sales breakdown |
| GET | `/reports/monthly-sales?month=&year=` | Monthly sales |
| GET | `/reports/top-products?startDate=&endDate=` | Top sellers |
| GET | `/reports/salesperson-performance?startDate=&endDate=` | Staff performance |
| GET | `/reports/state-wise-sales?startDate=&endDate=` | Geographic breakdown |
| GET | `/reports/export?reportType=&format=&startDate=&endDate=` | Export (CSV/XLSX/PDF) |

### Dispatch, Billing & Other (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/dispatch/queue` | Orders ready for dispatch (PAID/PACKED) |
| GET    | `/dispatch/labels/{orderId}` | Generate PDF dispatch label |
| POST   | `/dispatch/labels/bulk` | Bulk labels PDF |
| POST   | `/billing/export/vyapar` | Vyapar-compatible CSV export |
| GET    | `/billing/invoice/{orderId}` | GST invoice PDF |
| GET    | `/reviews/pending` | Pending review approvals |
| PATCH  | `/reviews/{id}/approve` | Approve a review |
| DELETE | `/reviews/{id}` | Delete a review |
| GET    | `/coupons` | List all coupons |
| POST   | `/coupons` | Create coupon |
| PATCH  | `/coupons/{id}/toggle` | Activate/deactivate coupon |
| DELETE | `/coupons/{id}` | Delete coupon |
| GET    | `/salesperson` | List salespersons |
| POST   | `/salesperson` | Create salesperson |
| POST   | `/salesperson/targets` | Set monthly target |

---

## Frontend Routes

### Admin Panel (`http://localhost:4200/`)

| Route | Page | Access |
|-------|------|--------|
| `/login` | Admin login | Public |
| `/dashboard` | KPI dashboard | All admin roles |
| `/orders` | Order list + filter | MANAGER, SALESPERSON, DISPATCHER |
| `/orders/new` | Create order wizard | MANAGER, SALESPERSON |
| `/orders/whatsapp` | WhatsApp order import | MANAGER, SALESPERSON |
| `/orders/:id` | Order detail + status timeline | All admin roles |
| `/products` | Product list | MANAGER, SALESPERSON |
| `/products/new` | Add product | MANAGER |
| `/products/:id/edit` | Edit product | MANAGER |
| `/customers` | Customer list | MANAGER, SALESPERSON, ACCOUNTANT |
| `/dispatch` | Dispatch queue + labels | DISPATCHER, MANAGER |
| `/billing` | Vyapar export + invoices | ACCOUNTANT, MANAGER |
| `/reports` | Sales reports + charts | MANAGER, ACCOUNTANT |
| `/targets` | Salesperson targets | TENANT_ADMIN |
| `/users` | User management | TENANT_ADMIN |
| `/reviews` | Review approval queue | MANAGER, TENANT_ADMIN |
| `/coupons` | Coupon management | MANAGER, TENANT_ADMIN |

### Public Storefront (`http://localhost:4200/store`)

| Route | Page |
|-------|------|
| `/store` | Landing page (featured products, categories, philosophy) |
| `/store/products` | Product catalog with category sidebar |
| `/store/products/:id` | Product detail with reviews and WhatsApp share |
| `/store/cart` | Shopping cart |
| `/store/checkout` | Checkout with COD / Razorpay payment + coupon field |
| `/store/wishlist` | Saved products |
| `/store/login` | Customer login |
| `/store/register` | Customer registration |
| `/store/profile` | Account settings (edit profile, change password) |
| `/store/orders` | Order history |
| `/store/orders/:id` | Order detail with status timeline |

---

## Features

### Admin Panel

**Order Management**
- Full lifecycle: NEW → CONFIRMED → PAID → PACKED → DISPATCHED → DELIVERED → RETURNED
- Stock automatically reduced on PACKED, restored on CANCELLED/RETURNED
- Status history with user, timestamp, and notes on every transition
- WhatsApp message parser: paste a customer's message → auto-extract name, phone, address, products, payment mode with fuzzy product matching and confidence scoring
- Bulk status update for dispatch batches
- Duplicate order detection (Jaccard similarity on product sets within 7 days)

**Products & Inventory**
- SKU, MRP, sale price, category, weight, HSN code, GST rate, image URL
- Stock management with full history (STOCK_IN / STOCK_OUT / ADJUSTMENT / RETURN)
- Low-stock threshold with admin alerts
- Product engagement tracking (view count, WhatsApp share count)

**Dispatch & Billing**
- PDF dispatch labels with Code128 barcode (single and bulk)
- Vyapar-compatible CSV export (daily, monthly, or selected orders)
- GST invoice PDF generation

**Reports**
- Dashboard KPIs (today's orders, revenue, pending, low-stock products)
- Daily and monthly sales reports
- Product-wise sales breakdown
- State/city-wise geographic reports
- Salesperson performance ranking
- Multi-format export: CSV, Excel (Apache POI), PDF

**Review Management**
- Admin approval queue for pending reviews
- Approve or delete with one click

**Coupon Management**
- Create PERCENT or FLAT discount codes
- Validity dates, minimum order amount, usage limits, per-user limits
- Toggle active/inactive, delete

### Public Storefront

- 18 Ayurvedic products with real product images
- Cart and wishlist (localStorage-based)
- COD or Razorpay online payment at checkout
- Coupon code field with live discount calculation
- Customer registration + JWT-authenticated account
- My Orders with full status timeline
- Product reviews with 5-star rating histogram and verified purchase badges
- WhatsApp share on every product card and product detail page

---

## Configuration

### Backend: `backend/src/main/resources/application-simple.yml`

```yaml
# Database
spring.datasource.url: jdbc:mysql://localhost:3306/shifa_db
spring.datasource.username: root
spring.datasource.password: root@123       # Change in production

# JWT
app.jwt.secret: <base64-encoded-secret>    # Change in production (min 32 bytes)
app.jwt.access-token-expiration-ms: 86400000   # 24 hours
app.jwt.refresh-token-expiration-ms: 604800000  # 7 days

# Razorpay (replace placeholders with real keys from dashboard.razorpay.com)
app.razorpay.key-id: rzp_test_PLACEHOLDER
app.razorpay.key-secret: PLACEHOLDER_SECRET

# Email (disabled by default; set env vars to enable)
spring.mail.host: smtp.gmail.com
spring.mail.port: 587
spring.mail.username: ${MAIL_USERNAME:noreply@shifa.com}
spring.mail.password: ${MAIL_PASSWORD:}
app.mail.enabled: ${MAIL_ENABLED:false}
app.mail.from: ${MAIL_FROM:noreply@shifa.com}
```

### Production environment variables

```bash
MAIL_ENABLED=true
MAIL_USERNAME=your@gmail.com
MAIL_PASSWORD=your_app_password
MAIL_FROM=orders@shifa.com
```
Generate a Gmail App Password at: https://myaccount.google.com/apppasswords

### Frontend: `frontend/src/environments/environment.ts`

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

---

## Roles & Permissions

Role hierarchy: `SUPER_ADMIN > TENANT_ADMIN > MANAGER > SALESPERSON`, with `TENANT_ADMIN > ACCOUNTANT` and `TENANT_ADMIN > DISPATCHER`.
A higher role automatically inherits all permissions of roles below it.

| Role | Can do |
|------|--------|
| `SUPER_ADMIN` | Everything (platform-level + all tenant operations) |
| `TENANT_ADMIN` | All operations within the tenant including user management |
| `MANAGER` | Orders, products, customers, reports, coupon/review management |
| `SALESPERSON` | Create orders, view orders, manage customers |
| `DISPATCHER` | View PAID/PACKED orders, generate dispatch labels |
| `ACCOUNTANT` | Record payments, run reports, billing exports |

**Default admin credentials** (change before production):
- Username: `admin`, Password: `admin123`, Role: `TENANT_ADMIN`

---

## Payment Gateway (Razorpay)

### How it works

1. Customer selects "Pay Online (Razorpay)" at checkout
2. Frontend calls `POST /api/storefront/payment/create-order` → receives `{razorpayOrderId, keyId}`
3. Frontend loads Razorpay checkout script from CDN and opens the payment modal
4. Customer completes payment; Razorpay callback fires with `{razorpay_order_id, razorpay_payment_id, razorpay_signature}`
5. Frontend calls `POST /api/storefront/payment/verify` → backend verifies HMAC-SHA256 signature
6. On verification success: order `paymentStatus` → PAID, `paymentMode` → ONLINE, Razorpay IDs stored

### Setup

1. Create a free account at [dashboard.razorpay.com](https://dashboard.razorpay.com)
2. Copy your test Key ID and Key Secret
3. Update `application-simple.yml`:
   ```yaml
   app.razorpay.key-id: rzp_test_YOUR_KEY_ID
   app.razorpay.key-secret: YOUR_KEY_SECRET
   ```
4. For production, use live keys (`rzp_live_...`) and add a webhook handler (see `PRE_LAUNCH_BACKLOG.md`)

---

## Email Notifications

Email is **disabled by default** so the app runs without SMTP configured.

To enable:
1. Create a Gmail App Password (requires 2FA on your Google account)
2. Set environment variables before starting the backend:
   ```
   MAIL_ENABLED=true
   MAIL_USERNAME=your@gmail.com
   MAIL_PASSWORD=app_password_here
   MAIL_FROM=orders@yourshop.com
   ```

What gets sent:
- **Order Confirmation** — on every successful storefront order (HTML email with items, totals, delivery address)
- **Payment Confirmation** — on successful Razorpay payment (includes payment reference)

---

## Coupon System

### Admin: create coupons at `/coupons`

| Field | Description |
|-------|-------------|
| Code | Unique code customers enter (case-insensitive) |
| Type | `PERCENT` (percentage off) or `FLAT` (fixed amount off) |
| Value | Percentage or rupee amount |
| Min Order | Minimum order subtotal to apply |
| Max Discount | Cap on PERCENT discounts (optional) |
| Usage Limit | Total redemptions across all customers |
| Per User Limit | Maximum uses per customer phone number |
| Valid Until | Expiry date |

### Sample coupons (pre-seeded)

| Code | Type | Value | Min Order | Notes |
|------|------|-------|-----------|-------|
| `WELCOME10` | PERCENT | 10% | ₹300 | Max ₹100 discount |
| `FLAT50` | FLAT | ₹50 | ₹500 | |
| `SHIFA20` | PERCENT | 20% | ₹800 | Max ₹200 discount |

---

## Storefront Customer Accounts

Customers can create an account at `/store/register` (separate from admin accounts):

- Email + password authentication (BCrypt, independent JWT)
- Profile stored in `storefront_users` table; address synced to `customers` table
- My Orders page with full status timeline
- Profile edit: name, phone, delivery address
- Change password

The storefront JWT uses the same `JwtTokenProvider` with role `STOREFRONT_USER`.

---

## Product Reviews

- Customers can submit reviews from the product detail page (name, 1–5 stars, title, text)
- Reviews are `is_approved = false` by default
- Admin approves at `/reviews` in the admin panel
- Approved reviews show on the product page with: average rating, 5-star histogram, verified purchase badge
- 47 sample reviews pre-seeded across all 18 products

---

## File Structure

```
Ayurveda Portal/
├── README.md                   ← This file
├── PRE_LAUNCH_BACKLOG.md       ← Feature roadmap and pre-launch checklist
├── SEED_DEMO_DATA.sql          ← Demo data (products, customers, orders, reviews, coupons)
├── SETUP_SHIFA_DB_SIMPLE.sql   ← Fresh DB setup script
├── SETUP_SHIFA_TENANT.sql      ← Legacy multi-tenant setup (not used in simple profile)
│
├── backend/
│   ├── pom.xml                 ← Maven dependencies
│   ├── src/main/
│   │   ├── java/com/ayurveda/platform/
│   │   │   ├── config/         ← SecurityConfig, DataSourceConfig, JacksonConfig, CacheConfig
│   │   │   ├── controller/     ← REST controllers (auth, orders, products, storefront, payment, coupons…)
│   │   │   ├── dto/            ← Request/response DTOs
│   │   │   ├── exception/      ← Custom exceptions + GlobalExceptionHandler
│   │   │   ├── master/         ← platform_users, company_config, email service, audit service
│   │   │   ├── security/       ← JWT filter, token provider, entry point
│   │   │   ├── tenant/         ← All business entities + repositories + services
│   │   │   └── util/           ← OrderNumberGenerator, WhatsAppTextParser
│   │   └── resources/
│   │       ├── application.yml             ← Base config (profile selector)
│   │       ├── application-simple.yml      ← Active single-client config
│   │       ├── application-dev.yml         ← Multi-tenant dev config (inactive)
│   │       └── db/migration/               ← Flyway SQL migrations V1–V9
│   └── src/test/               ← 668 unit, property-based, and integration tests
│
└── frontend/
    ├── angular.json
    ├── public/images/shifa/    ← Logo + 18 product images
    └── src/app/
        ├── core/               ← auth service, API service, JWT interceptor, guards
        ├── models/             ← TypeScript interfaces
        ├── services/           ← storefront-api.service, storefront-auth.service, cart, wishlist
        ├── shared/
        │   ├── layout/         ← Responsive admin shell (drawer nav for mobile)
        │   └── ui/             ← icon.component (SVG icon set)
        └── pages/
            ├── dashboard/      ← KPI stats dashboard
            ├── orders/         ← List, detail, create, WhatsApp import
            ├── products/       ← List, form
            ├── customers/      ← List
            ├── dispatch/       ← Dispatch queue + label generation
            ├── billing/        ← Vyapar export, invoices
            ├── reports/        ← Sales/product/geographic reports + charts
            ├── targets/        ← Salesperson targets
            ├── users/          ← User management
            ├── reviews/        ← Review approval
            ├── coupons/        ← Coupon management
            ├── login/          ← Admin login
            └── storefront/
                ├── landing/    ← Store homepage
                ├── catalog/    ← Product listing
                ├── product-detail/ ← Product + reviews + WhatsApp share
                ├── cart/       ← Shopping cart
                ├── checkout/   ← Checkout with Razorpay + coupon
                ├── wishlist/   ← Saved items
                ├── auth/       ← Customer login + registration
                ├── profile/    ← Account settings
                └── my-orders/  ← Order history + detail
```

---

## Pre-Launch Backlog

See [`PRE_LAUNCH_BACKLOG.md`](PRE_LAUNCH_BACKLOG.md) for a full prioritized roadmap.

### Top 5 items before going live

| # | Feature | Priority |
|---|---------|----------|
| 1 | **Razorpay webhook handler** — server-side payment confirmation even if browser closes | 🔴 Critical |
| 2 | **Password reset (email/OTP)** — customers can't recover locked accounts | 🔴 Critical |
| 3 | **SMS notifications** — order confirmation + dispatch alert via Fast2SMS/MSG91 | 🔴 Critical |
| 4 | **HTTPS setup** — SSL termination at Nginx/ALB | 🔴 Critical |
| 5 | **Rate limiting on auth** — prevent brute-force on login/register | 🔴 Critical |

---

## Original Brief

The project was originally conceived as a multi-tenant SaaS platform serving multiple Ayurveda vendors,
with the first client being Shifa Herbal Remedies. The architecture uses a Database-per-Tenant model
(AbstractRoutingDataSource with per-tenant MySQL databases) backed by a master database for tenant
registry and platform users.

The `simple` profile currently active implements the single-client variant for Shifa,
with all the same business capabilities but without tenant routing complexity.
The `dev` profile activates the full multi-tenant stack when needed.

---

*Last updated: June 2026 · Version: 1.0.0*
