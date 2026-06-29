# Shifa Ayurveda — Pre-Launch Feature Backlog

> **What's already live:** Core order management, dispatch, billing, reporting, WhatsApp parsing,
> storefront with customer accounts, product reviews, WhatsApp sharing, Razorpay payment gateway,
> order confirmation email (configurable), and coupon/discount codes.

Items are grouped by area and ordered by impact within each group.

---

## 🔴 Critical — Must-have before going live

### Security & Auth
- [ ] **Password reset via email/OTP** — storefront users have no recovery path if they lose access.
  `POST /storefront/auth/forgot-password` (send reset link) + `POST /storefront/auth/reset-password` (consume token).
  Requires a `password_reset_tokens` table (token, email, expires_at, used).

- [ ] **Email verification on registration** — `is_verified` column in `storefront_users` is never set
  to `true`. Add a verification email flow at registration; optionally block checkout for unverified users.

- [ ] **Rate limiting on auth endpoints** — `/storefront/auth/login`, `/storefront/auth/register`
  have no brute-force protection. Use Spring's `spring-boot-starter-actuator` + Bucket4j or a
  simple in-memory rate limiter (e.g. Guava LoadingCache per IP).

- [ ] **HTTPS enforcement** — all traffic must be over SSL in production. Configure SSL termination
  at the reverse proxy (Nginx/AWS ALB) or via `server.ssl.*` in Spring Boot.

- [ ] **XSS sanitization audit** — verify `order.notes`, review text, and address fields are
  sanitised server-side (Spring Validation + HtmlUtils.htmlEscape on free-text inputs).

- [ ] **Admin session timeout / 2FA** — admin accounts have no second factor; consider TOTP
  (Google Authenticator) for TENANT_ADMIN users.

### Payments
- [ ] **Razorpay webhook handler** — the current verify endpoint relies on the frontend callback.
  A server-side webhook (`POST /webhook/razorpay`) verifies the `X-Razorpay-Signature` header and
  updates payment status even if the customer closes the browser before the callback fires.
  Required for reliable payment confirmation.
  Endpoint path must be `permitAll()` in SecurityConfig.

- [ ] **Payment failure handling** — define what happens when Razorpay payment fails mid-flow
  (order should remain NEW/unpaid; display a "retry payment" option linked to the existing order).

- [ ] **Refund handling** — no refund workflow exists for RETURNED orders. Razorpay refunds can be
  initiated via `client.payments.refund(paymentId, params)` in `RazorpayService`.

- [ ] **GST invoice PDF surfaced to customer** — the invoice PDF endpoint (`GET /api/billing/invoice/{orderId}`)
  is built; it just needs to be linked from the storefront "My Orders" order detail page
  and optionally emailed post-delivery.

### Storefront UX
- [ ] **Order cancellation by customer** — customers can view orders but cannot cancel from the storefront.
  Add `POST /storefront/my-orders/{orderId}/cancel` (allowed only for NEW/CONFIRMED status).

- [ ] **SMS notifications** — many Indian customers prefer SMS over email. Integrate Twilio/Fast2SMS/MSG91
  for order confirmation and dispatch SMS. Low effort once `EmailNotificationService` pattern is established.

- [ ] **Dispatch notification to customer** — currently no notification fires when admin marks an order
  DISPATCHED. Hook into `OrderService.updateOrderStatus()` to send an email/SMS with the order number
  and tracking link when status → DISPATCHED.

---

## 🟡 Important — Significantly improves conversion & retention

### Storefront — Discovery & Conversion
- [ ] **URL-shareable product search** — the `GET /storefront/products/search?query=` endpoint exists
  but the catalog UI doesn't wire search to the URL (`?q=...` query param). Add debounced search
  input in the catalog toolbar; pre-populate from the URL on page load; update URL on search.

- [ ] **Product filtering** — add price-range, in-stock-only, and category filters to the catalog sidebar.
  These are all query-param driven on the existing paginated `/storefront/products` endpoint.

- [ ] **Cart persistence (server-side)** — current cart lives only in `localStorage`. A logged-in customer
  loses their cart on a new device. Options: store cart JSON against `storefront_users.cart_json` (simplest),
  or a dedicated `cart_items` table. Sync on login.

- [ ] **Out-of-stock notifications** — "Notify me when back in stock" (email capture against a product).
  Requires a `stock_notifications` table (product_id, email, created_at) and a trigger/job when
  `stockQuantity` increases above 0.

- [ ] **Recently Viewed products** — `view_count` tracking is already wired; store the last 8 viewed
  product IDs in `localStorage` and display a "Recently Viewed" row on landing and catalog pages.

- [ ] **Dynamic SEO meta tags** — product pages need `<title>` and `<meta name="description">` set
  dynamically per product (Angular `Title` and `Meta` services). Critical for Google indexing.

- [ ] **Product image gallery** — products have one `image_url`. A `product_images` table
  (product_id, url, sort_order) with a thumbnail strip on the product detail page would improve conversion.

### Admin & Operations
- [ ] **Bulk product import (CSV)** — uploading 50+ products one-by-one via the form is impractical.
  Add `POST /api/products/import/csv` (multipart upload, parse with Apache CSV, validate SKU uniqueness,
  return per-row success/failure report).

- [ ] **Low stock email alert** — `low_stock_threshold` is tracked but nothing fires when a product
  crosses it. Wire a check in `ProductManagementService.updateStock()`: when `newQty <= threshold`,
  send a low-stock alert email to the admin email from `company_config`.

- [ ] **Courier API integration** — dispatch labels are generated but there's no courier API (Shiprocket,
  Delhivery, Ecom Express). Auto-create a shipment on DISPATCHED status and store the AWB number;
  show tracking URL on the storefront order detail page.

- [ ] **Stock purchase / goods receipt** — currently only STOCK_OUT is automated. Add a
  `POST /api/products/{id}/purchase-stock` endpoint (STOCK_IN operation) so admins can record
  stock purchases without using the manual adjustment endpoint.

- [ ] **Partial return** — `ReturnOrderRequest.returnedItemIds` exists in the DTO but only
  full-order returns are implemented. Wire partial item-level returns to restore stock selectively.

- [ ] **Shipping charge rules** — shipping is a flat field on each order. Build a
  `shipping_rules` table (min_amount, weight_limit, charge) and a resolution service so shipping
  is calculated automatically at checkout rather than hardcoded.

---

## 🟢 Growth — Differentiators post-launch

### Customer Engagement
- [ ] **Loyalty / points program** — every ₹X spent = Y points; points redeemable at checkout.
  Requires `loyalty_accounts` (customer_id, points_balance), `loyalty_transactions` tables and a
  redemption endpoint similar to coupon validation.

- [ ] **Product bundles / kits** — a `product_bundles` table (bundle_id, product_id, quantity,
  bundle_price) with a storefront landing section "Shop Combos". Common in Ayurveda.

- [ ] **Subscription orders** — "Subscribe & Save" for consumables. Monthly auto-create storefront
  orders. Requires a `subscriptions` table, a scheduled job, and a customer management UI.

- [ ] **Dosha / skin quiz** — a guided questionnaire routing customers to recommended products
  (like Shankara's skin quiz). Product tags for Vata/Pitta/Kapha doshas + a quiz result page
  showing filtered products.

- [ ] **Product Q&A** — customers ask questions; admin or other customers answer. A `product_questions`
  table (product_id, question, answer, is_answered) with a public/admin UI.

- [ ] **Multi-language support** — Hindi (`hi`) for broader Indian reach. Angular `@angular/localize`
  with `i18n` attributes. Catalog and product names could remain in English; UI chrome (buttons,
  labels, nav) translatable.

- [ ] **Compare products** — side-by-side 2-3 product comparison (price, weight, description, rating).
  A floating compare bar at bottom of catalog; a dedicated `/store/compare` page.

- [ ] **Referral program** — unique referral links per storefront user; referee gets a welcome coupon;
  referrer earns points. Requires `referral_codes` table and coupon auto-generation.

### Analytics & Growth
- [ ] **Google Analytics 4 / GTM** — add `gtag('event', ...)` calls for: `add_to_cart`,
  `begin_checkout`, `purchase` (on order success). Revenue tracking is the most valuable event.
  Wire via Angular `afterNextRender` or a GTM container tag.

- [ ] **Abandoned cart recovery** — detect when cart has items but checkout was not completed
  (use a `localStorage` timestamp + a storefront user's email). Send a reminder email after 24h
  via a scheduled job or a lightweight worker.

- [ ] **Admin analytics — Engagement** — product view_count and whatsapp_share_count are stored but
  not surfaced in the admin reports. Add a "Product Engagement" tab to the Reports page
  showing views, shares, and conversion rate (views ÷ orders containing that product).

### Technical / Infrastructure
- [ ] **Razorpay webhook** — see Critical section above; also relevant here for reliability.

- [ ] **Redis / external cache** — the product-categories cache currently uses in-memory
  `ConcurrentMapCacheManager` which is lost on restart and is not shared across instances.
  Replace with Redis (`spring-boot-starter-data-redis`, `RedisCacheManager`) for production.

- [ ] **Database backups** — set up daily `mysqldump` to S3 or Azure Blob Storage with 30-day
  retention. No automated backup currently exists.

- [ ] **Error monitoring (Sentry)** — add `sentry-spring-boot-starter` (backend) and
  `@sentry/angular` (frontend). Production errors are currently invisible unless someone reads logs.

- [ ] **Image upload to cloud storage** — product images are static files in the Angular `public/`
  folder. Move to S3/Cloudinary for scalable delivery, CDN caching, and admin image upload
  (`POST /api/products/{id}/image` → multipart upload → return CDN URL).

- [ ] **API versioning** — prefix all endpoints with `/v1/` to allow non-breaking changes later.
  Can be done at the `server.servlet.context-path` level or with a `@RequestMapping` prefix.

- [ ] **Containerization** — add `Dockerfile` (multi-stage: Maven build → slim JRE image) and
  `docker-compose.yml` (backend + MySQL + Nginx) to make deployment reproducible. Currently
  deployment is entirely manual.

- [ ] **Production config management** — sensitive values (DB password, JWT secret, Razorpay keys,
  SMTP credentials) are in `application-simple.yml`. Move to environment variables, AWS SSM
  Parameter Store, or Vault before production.

---

## 📋 Suggested launch sequence

### Phase 1 — Pre-launch hardening (2–4 weeks)
1. Password reset flow (critical for any real user base)
2. Razorpay webhook handler (prevents lost payments)
3. SMS notifications (order confirmation + dispatch)
4. HTTPS setup
5. Rate limiting on auth endpoints

### Phase 2 — First 30 days post-launch
6. Product search + filtering in catalog
7. Cart persistence for logged-in users
8. GST invoice email post-delivery
9. Low stock email alerts for admin
10. Google Analytics 4 purchase event

### Phase 3 — Scale & retention (30–90 days)
11. Loyalty points
12. Courier API integration (Shiprocket/Delhivery)
13. Abandoned cart recovery
14. Redis cache + Sentry
15. Database automated backups

---

*Last updated: 2026-06-29*
