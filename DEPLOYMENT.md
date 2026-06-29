# Deployment Guide — Zero-Cost Cloud Setup

Deploy the full stack for ₹0/month:

| Layer | Service | Cost |
|-------|---------|------|
| MySQL 8 database | Aiven | Free (1 GB) |
| Spring Boot backend | Railway | Free ($5/month credit) |
| Angular frontend | Vercel | Free forever |

**Time to complete: ~45 minutes**

---

## Prerequisites

- GitHub account with the `Ayurveda-Portal` repo pushed ✅
- Credit/debit card (Aiven requires one for identity; will not charge on free tier)
- Gmail account (for order confirmation emails — optional, can skip initially)

---

## Step 1 — MySQL on Aiven

### 1.1 Create account
1. Go to [https://aiven.io](https://aiven.io) → **Sign up** (use GitHub login for speed)
2. Verify your email

### 1.2 Create a MySQL service
1. Click **+ Create service**
2. Choose **MySQL**
3. Select plan: **Free** (shown as "Hobbyist" or "Free tier")
4. Cloud/region: pick the closest to India — **AWS ap-south-1 (Mumbai)** if available, else any
5. Service name: `shifa-mysql` (or anything)
6. Click **Create service** — takes ~2 minutes to provision

### 1.3 Get the connection details
1. Click on your new service → **Overview** tab
2. Copy these values (you'll need them for Railway):

```
Host:     mysql-xxxxxxx.aiven.io
Port:     3306
Database: defaultdb
User:     avnadmin
Password: xxxxxxxxxxxxxxxxxx
```

3. Also copy the full **Service URI** — it looks like:
   ```
   mysql://avnadmin:PASSWORD@mysql-xxx.aivencloud.com:PORT/defaultdb?ssl-mode=REQUIRED
   ```

### 1.4 Build the JDBC URL
Convert the Service URI to JDBC format for Spring Boot:
```
jdbc:mysql://mysql-xxx.aivencloud.com:PORT/defaultdb?useSSL=true&requireSSL=true&serverTimezone=Asia/Kolkata&useUnicode=true&characterEncoding=UTF-8
```
**Save this string** — you'll paste it into Railway in the next step.

### 1.5 Seed the admin user
Aiven gives you a MySQL shell in the browser. Go to **Query** tab and run:

```sql
-- Create the admin user (password: admin123)
INSERT INTO platform_users
  (tenant_id, username, email, password_hash, role, full_name, is_active)
VALUES
  (NULL, 'admin', 'admin@shifa.com',
   '$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y',
   'TENANT_ADMIN', 'Administrator', b'1')
ON DUPLICATE KEY UPDATE role='TENANT_ADMIN', is_active=b'1';

-- Seed company config so the app starts without error
INSERT INTO company_config
  (company_name, address, phone, email,
   low_stock_threshold, order_number_prefix,
   default_tax_rate, cgst_rate, sgst_rate, igst_rate,
   enable_whatsapp_parsing, enable_storefront,
   default_shipping_charge, minimum_order_value,
   duplicate_check_days, fuzzy_match_threshold)
SELECT
  'Shifa Ayurveda','Not Configured','0000000000','info@shifa.com',
  10,'ORD',
  18.00,9.00,9.00,18.00,
  b'1',b'1',
  0.00,0.00,
  7,0.60
WHERE NOT EXISTS (SELECT 1 FROM company_config);
```

> **Note:** Hibernate `ddl-auto: update` will create all tables on first backend start,
> so you only need to run the above *after* the backend has started once.
> Alternatively, run the full `SEED_DEMO_DATA.sql` from the repo to get demo products and orders.

---

## Step 2 — Spring Boot Backend on Railway

### 2.1 Create account
1. Go to [https://railway.app](https://railway.app) → **Login with GitHub**
2. Authorize Railway to access your repos

### 2.2 Create a new project
1. Click **New Project** → **Deploy from GitHub repo**
2. Select `Ayurveda-Portal`
3. Railway will detect the project — click **Deploy**

### 2.3 Configure the service
1. Click on the deployed service → **Settings** tab
2. Set **Root Directory**: `backend`
3. Set **Start command** (leave blank — Railway auto-detects Maven)
4. Under **Build**, confirm it runs `mvn clean package -DskipTests`

### 2.4 Set environment variables
Click **Variables** tab → add each one:

| Variable | Value |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `simple` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql-xxx.aivencloud.com:PORT/defaultdb?useSSL=true&requireSSL=true&serverTimezone=Asia/Kolkata&useUnicode=true&characterEncoding=UTF-8` |
| `SPRING_DATASOURCE_USERNAME` | `avnadmin` |
| `SPRING_DATASOURCE_PASSWORD` | *(password from Aiven)* |
| `APP_JWT_SECRET` | *(generate one — see below)* |
| `CORS_ALLOWED_ORIGINS` | `https://YOUR-APP.vercel.app` *(fill in after Vercel step)* |
| `MAIL_ENABLED` | `false` |
| `SERVER_PORT` | `8080` |

**Generating a JWT secret** — use this ready-made value (already validated for Railway):
```
U2hpZmFBeXVydmVkYVNlY3JldEtleTIwMjZQcm9kdWN0aW9uSldUU2lnbmluZ0tleUZvclRva2VuR2VuZXJhdGlvbiE=
```
Paste it exactly as-is — no quotes, no spaces before or after.

> **Why not generate your own?** PowerShell's `RandomNumberGenerator` and `openssl rand` output can
> include `+` `/` and line-breaks that look clean on screen but get corrupted when pasted into
> Railway's variable editor, causing `Illegal base64 character` crashes. The value above was
> generated on your machine and verified clean.

### 2.5 Get your Railway URL
1. After the first successful deploy, go to **Settings** → **Domains**
2. Click **Generate Domain** → you get something like `ayurveda-portal-backend.up.railway.app`
3. **Save this URL** — needed for Vercel and `vercel.json`

### 2.6 Verify the backend is running
Open in your browser:
```
https://ayurveda-portal-backend.up.railway.app/api/storefront/config
```
You should see a JSON response with `companyName: "Shifa Ayurveda"`.

---

## Step 3 — Update `vercel.json` with your Railway URL

In `frontend/vercel.json`, replace `REPLACE_WITH_RAILWAY_URL` with your actual Railway domain:

```json
{
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://ayurveda-portal-backend.up.railway.app/api/:path*"
    },
    {
      "source": "/((?!api).*)",
      "destination": "/index.html"
    }
  ]
}
```

Commit and push this change:
```bash
git add frontend/vercel.json
git commit -m "Set Railway backend URL in Vercel proxy"
git push
```

---

## Step 4 — Angular Frontend on Vercel

### 4.1 Create account
1. Go to [https://vercel.com](https://vercel.com) → **Continue with GitHub**
2. Authorize Vercel

### 4.2 Import the project
1. Click **Add New → Project**
2. Find `Ayurveda-Portal` in the repo list → **Import**

### 4.3 Configure build settings

**Important:** Do NOT let Vercel auto-detect. Set these manually:

| Setting | Value |
|---------|-------|
| Framework Preset | **Other** (not Angular — select "Other") |
| Root Directory | `frontend` |
| Build Command | `npm run build` |
| Output Directory | `dist/frontend/browser` ← **must include `/browser`** |
| Install Command | `npm install` |

> The `/browser` suffix is required for Angular 19's `application` builder.
> If Vercel auto-detects Angular and sets the output to `dist/frontend` (without `/browser`),
> you will get a `404: NOT_FOUND` error on every page.

### 4.4 Before deploying — update `vercel.json`

You must replace the Railway URL placeholder in `frontend/vercel.json` first.
Get your Railway URL from Railway → your service → **Settings → Domains**.

Then edit `frontend/vercel.json`:
```json
{
  "outputDirectory": "dist/frontend/browser",
  "buildCommand": "npm run build",
  "installCommand": "npm install",
  "framework": null,
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://YOUR-APP.up.railway.app/api/:path*"
    },
    {
      "source": "/((?!api).*)",
      "destination": "/index.html"
    }
  ]
}
```

Commit and push before importing to Vercel:
```bash
git add frontend/vercel.json
git commit -m "Set Railway backend URL for Vercel proxy"
git push
```

### 4.5 Deploy
Click **Deploy**. The first build takes ~3 minutes.

When done you get a URL like `https://ayurveda-portal.vercel.app`.

### 4.5 Update CORS on Railway
Go back to Railway → Variables → update:
```
CORS_ALLOWED_ORIGINS = https://ayurveda-portal.vercel.app
```
Railway will auto-redeploy.

### 4.6 Add a custom domain (optional, free)
In Vercel → your project → **Settings → Domains**, add your own domain if you have one.

---

## Step 5 — Verify everything works

Open your Vercel URL in a browser and run through this checklist:

- [ ] `https://your-app.vercel.app/store` loads the storefront
- [ ] `https://your-app.vercel.app/store/products` shows products (after seeding)
- [ ] `https://your-app.vercel.app/login` → login with `admin` / `admin123` → dashboard loads
- [ ] Dashboard shows stats (even if zeros)
- [ ] Create a test order from the storefront checkout (COD)
- [ ] Verify the order appears in the admin Orders page

---

## Step 6 — Seed demo data (optional)

To populate the database with 18 products and demo orders, run `SEED_DEMO_DATA.sql` against your Aiven MySQL.

Use the Aiven Query tab or any MySQL client:
```bash
mysql -h mysql-xxx.aivencloud.com -P PORT -u avnadmin -p defaultdb < SEED_DEMO_DATA.sql
```

---

## Step 7 — Enable email notifications (optional)

When you're ready for real order confirmation emails:

1. Create a Gmail App Password:
   - Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
   - Select app: **Mail**, device: **Other** → name it `Shifa`
   - Copy the 16-character password

2. In Railway → Variables, add/update:
   ```
   MAIL_ENABLED     = true
   MAIL_USERNAME    = your-gmail@gmail.com
   MAIL_PASSWORD    = xxxx xxxx xxxx xxxx   (the 16-char App Password)
   MAIL_FROM        = orders@shifa.com
   ```

---

## Step 8 — Razorpay (when ready)

1. Create account at [dashboard.razorpay.com](https://dashboard.razorpay.com)
2. Get test keys: Settings → API Keys → Generate Test Key
3. In Railway → Variables:
   ```
   RAZORPAY_KEY_ID     = rzp_test_xxxxxxxxxx
   RAZORPAY_KEY_SECRET = xxxxxxxxxxxxxxxxxx
   ```
4. Test with Razorpay's test card: `4111 1111 1111 1111`, any future date, any CVV

---

## Troubleshooting

### `404: NOT_FOUND` on all pages (Vercel)
Vercel can't find the build output. Two possible causes:

1. **Wrong output directory** — in your Vercel project settings, go to **Settings → General → Build & Output Settings** and set Output Directory to `dist/frontend/browser` (must include `/browser`). Redeploy.

2. **`vercel.json` not committed** — the `frontend/vercel.json` file must be pushed to GitHub before Vercel can pick it up. Check `git log` to confirm it's in the repo.

### `Illegal base64 character` crash on startup
The `APP_JWT_SECRET` variable has a space, newline, or non-base64 character in it.
Delete the variable in Railway and re-add it using **only** this value (no quotes, no spaces):
```
U2hpZmFBeXVydmVkYVNlY3JldEtleTIwMjZQcm9kdWN0aW9uSldUU2lnbmluZ0tleUZvclRva2VuR2VuZXJhdGlvbiE=
```
Then redeploy.

### Backend won't start
Check Railway logs (service → **Deploy** tab → click the latest deploy). Common issues:
- **DB connection refused** — wrong Aiven URL or port; ensure `?useSSL=true&requireSSL=true` is in the URL
- **ConfigurationService failed** — `company_config` table is empty; run the seed SQL from Step 1.5
- **Port conflict** — ensure `SERVER_PORT=8080` is set in Railway env vars

### Frontend shows "Cannot connect to API"
- Open browser DevTools → Network tab → check which URL the API calls are going to
- If calls go to `localhost:8080`, the `vercel.json` rewrite isn't deployed yet — push the updated file
- If calls go to the Railway URL and get 403/404, check CORS: `CORS_ALLOWED_ORIGINS` must exactly match your Vercel URL (no trailing slash)

### Login returns 401
- The `platform_users` table is empty — run the admin seed SQL from Step 1.5
- The `company_config` table is empty — run the config seed SQL from Step 1.5

### Vercel build fails
Common Angular build errors:
- **Budget exceeded** — already handled in `angular.json` (budgets raised)
- **node_modules missing** — ensure Install Command is `npm install`
- **Output directory wrong** — must be `dist/frontend/browser` (note the `/browser` suffix for Angular 19)

---

## Architecture after deployment

```
User browser
     │
     ▼
Vercel (Angular)  ──── /api/* proxy ────►  Railway (Spring Boot)
https://your-app.vercel.app               https://xxx.up.railway.app
                                                    │
                                                    ▼
                                          Aiven MySQL (Free)
                                          mysql-xxx.aivencloud.com
```

The Vercel proxy (`vercel.json`) forwards all `/api/*` requests to Railway, which means:
- No CORS issues (same origin from the browser's perspective)
- No hardcoded backend URL in the Angular build
- Works automatically for all API calls

---

## Free tier limits summary

| Service | Limit | Your usage |
|---------|-------|-----------|
| Vercel | Unlimited deployments, 100 GB bandwidth/month | ~10 MB per deploy |
| Railway | $5/month credit (~500 hours of 0.5 vCPU/512MB) | ~720 hours/month needed — upgrade if exceeded |
| Aiven MySQL | 1 vCPU, 1 GB storage | ~50 MB currently |

**When to upgrade:**
- Railway: if you get real daily orders, consider the $5/month Developer plan for stable 24/7 uptime
- Aiven: at ~800 MB storage (run `SELECT table_schema, ROUND(SUM(data_length+index_length)/1024/1024,1) MB FROM information_schema.tables GROUP BY table_schema;` to check)
- Vercel: likely never on free tier for this app size

---

*Last updated: June 2026*
