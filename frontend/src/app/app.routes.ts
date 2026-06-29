import { Routes } from '@angular/router';
import { AuthGuard, RoleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // Public
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },

  // ── STOREFRONT (Public) ──────────────────────────────────────────────
  {
    path: 'store',
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/storefront/landing/storefront-landing.component').then(m => m.StorefrontLandingComponent)
      },
      {
        path: 'products',
        loadComponent: () => import('./pages/storefront/catalog/storefront-catalog.component').then(m => m.StorefrontCatalogComponent)
      },
      {
        path: 'products/:id',
        loadComponent: () => import('./pages/storefront/product-detail/product-detail.component').then(m => m.ProductDetailComponent)
      },
      {
        path: 'cart',
        loadComponent: () => import('./pages/storefront/cart/cart.component').then(m => m.CartComponent)
      },
      {
        path: 'checkout',
        loadComponent: () => import('./pages/storefront/checkout/checkout.component').then(m => m.CheckoutComponent)
      },
      {
        path: 'wishlist',
        loadComponent: () => import('./pages/storefront/wishlist/wishlist.component').then(m => m.WishlistComponent)
      },
      {
        path: 'login',
        loadComponent: () => import('./pages/storefront/auth/storefront-login.component').then(m => m.StorefrontLoginComponent)
      },
      {
        path: 'register',
        loadComponent: () => import('./pages/storefront/auth/storefront-register.component').then(m => m.StorefrontRegisterComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./pages/storefront/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'orders',
        loadComponent: () => import('./pages/storefront/my-orders/my-orders.component').then(m => m.MyOrdersComponent)
      },
      {
        path: 'orders/:id',
        loadComponent: () => import('./pages/storefront/my-orders/order-detail.component').then(m => m.StorefrontOrderDetailComponent)
      }
    ]
  },

  // Authenticated routes with layout shell
  {
    path: '',
    loadComponent: () => import('./shared/layout/layout.component').then(m => m.LayoutComponent),
    canActivate: [AuthGuard],
    children: [

      // Dashboard
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },

      // ── PRODUCTS ─────────────────────────────────────────────────────────
      {
        path: 'products',
        loadComponent: () => import('./pages/products/product-list.component').then(m => m.ProductListComponent)
      },
      {
        path: 'products/new',
        loadComponent: () => import('./pages/products/product-form.component').then(m => m.ProductFormComponent)
      },
      {
        path: 'products/:id/edit',
        loadComponent: () => import('./pages/products/product-form.component').then(m => m.ProductFormComponent)
      },

      // ── ORDERS ───────────────────────────────────────────────────────────
      {
        path: 'orders',
        loadComponent: () => import('./pages/orders/order-list.component').then(m => m.OrderListComponent)
      },
      {
        path: 'orders/new',
        loadComponent: () => import('./pages/orders/order-create.component').then(m => m.OrderCreateComponent)
      },
      {
        path: 'orders/whatsapp',
        loadComponent: () => import('./pages/orders/whatsapp-import.component').then(m => m.WhatsAppImportComponent)
      },
      {
        path: 'orders/:id',
        loadComponent: () => import('./pages/orders/order-detail.component').then(m => m.OrderDetailComponent)
      },

      // ── CUSTOMERS ────────────────────────────────────────────────────────
      {
        path: 'customers',
        loadComponent: () => import('./pages/customers/customer-list.component').then(m => m.CustomerListComponent)
      },

      // ── DISPATCH ─────────────────────────────────────────────────────────
      {
        path: 'dispatch',
        loadComponent: () => import('./pages/dispatch/dispatch.component').then(m => m.DispatchComponent)
      },

      // ── BILLING ──────────────────────────────────────────────────────────
      {
        path: 'billing',
        loadComponent: () => import('./pages/billing/billing.component').then(m => m.BillingComponent)
      },

      // ── REPORTS ──────────────────────────────────────────────────────────
      {
        path: 'reports',
        loadComponent: () => import('./pages/reports/reports.component').then(m => m.ReportsComponent)
      },

      // ── TARGETS (Admin +) ────────────────────────────────────────────────
      {
        path: 'targets',
        loadComponent: () => import('./pages/targets/targets.component').then(m => m.TargetsComponent),
        canActivate: [RoleGuard],
        data: { roles: ['TENANT_ADMIN', 'SUPER_ADMIN'] }
      },

      // ── USERS (Tenant Admin +) ────────────────────────────────────────────
      {
        path: 'users',
        loadComponent: () => import('./pages/users/users.component').then(m => m.UsersComponent),
        canActivate: [RoleGuard],
        data: { roles: ['TENANT_ADMIN', 'SUPER_ADMIN'] }
      },

      // ── REVIEWS ────────────────────────────────────────────────────────────
      {
        path: 'reviews',
        loadComponent: () => import('./pages/reviews/reviews.component').then(m => m.ReviewsComponent),
        canActivate: [RoleGuard],
        data: { roles: ['TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER'] }
      },

      // ── COUPONS ─────────────────────────────────────────────────────────────
      {
        path: 'coupons',
        loadComponent: () => import('./pages/coupons/coupons.component').then(m => m.CouponsComponent),
        canActivate: [RoleGuard],
        data: { roles: ['TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER'] }
      },

      // ── SUPER ADMIN ───────────────────────────────────────────────────────
      {
        path: 'admin/tenants',
        loadComponent: () => import('./pages/admin/admin-tenants.component').then(m => m.AdminTenantsComponent),
        canActivate: [RoleGuard],
        data: { roles: ['SUPER_ADMIN'] }
      },
      {
        path: 'admin/analytics',
        loadComponent: () => import('./pages/placeholder/placeholder.component').then(m => m.PlaceholderComponent),
        canActivate: [RoleGuard],
        data: { title: 'Platform Analytics', icon: '⚙️', description: 'Cross-tenant analytics dashboard — planned for v2', roles: ['SUPER_ADMIN'] }
      },

      // Default redirect
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },

  // Catch-all
  { path: '**', redirectTo: 'login' }
];
