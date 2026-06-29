// Core models for the Ayurveda platform

// --- Auth ---
export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
  fullName: string;
  email: string;
  role: UserRole;
  tenantKey: string;
  companyName: string;
  uiConfig: TenantUiConfig | null;
}

// --- User ---
export type UserRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'MANAGER' | 'SALESPERSON' | 'DISPATCHER';

export interface User {
  userId: number;
  username: string;
  fullName: string;
  email: string;
  role: UserRole;
  tenantKey: string;
  companyName: string;
}

// --- Tenant ---
export interface TenantUiConfig {
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  logoUrl: string;
  faviconUrl: string;
  fontFamily: string;
  customCss: string;
  storefrontEnabled: boolean;
}

export interface Tenant {
  id: number;
  tenantKey: string;
  companyName: string;
  domain: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'ONBOARDING';
  subscriptionPlan: string;
  contactEmail: string;
  contactPhone: string;
  createdAt: string;
}

// --- Product ---
export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string;
  category: string;
  mrp: number;
  salePrice: number;
  unit: string;
  weightGrams: number;
  hsnCode: string;
  gstRate: number;
  imageUrl: string;
  isActive: boolean;
  stockQuantity: number;
  lowStockThreshold: number;
  createdAt: string;
  updatedAt: string;
}

// --- Customer ---
export interface Customer {
  id: number;
  name: string;
  phone: string;
  email: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  pincode: string;
  gstin: string;
}

// --- Order ---
export type OrderStatus = 'NEW' | 'CONFIRMED' | 'PAID' | 'PACKED' | 'DISPATCHED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED';
export type OrderSource = 'WHATSAPP' | 'MANUAL' | 'STOREFRONT' | 'API';
export type PaymentMode = 'COD' | 'UPI' | 'BANK_TRANSFER' | 'ONLINE' | 'CREDIT';
export type PaymentStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'REFUNDED';

export interface Order {
  id: number;
  orderNumber: string;
  customer: Customer;
  salespersonId: number;
  orderSource: OrderSource;
  rawWhatsappText: string;
  status: OrderStatus;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  shippingCharge: number;
  totalAmount: number;
  paymentMode: PaymentMode;
  paymentStatus: PaymentStatus;
  notes: string;
  orderDate: string;
  dispatchedAt: string;
  deliveredAt: string;
  items: OrderItem[];
  createdAt: string;
}

export interface OrderItem {
  id: number;
  productId: number;
  productNameSnapshot: string;
  skuSnapshot: string;
  quantity: number;
  unitPrice: number;
  mrpSnapshot: number;
  discount: number;
  taxAmount: number;
  lineTotal: number;
}

// --- WhatsApp Parser ---
export interface ParsedWhatsAppOrder {
  customer: {
    name: string;
    phone: string;
    address: string;
    pincode: string;
  };
  items: ParsedItem[];
  warnings: string[];
  rawText: string;
}

export interface ParsedItem {
  rawText: string;
  quantity: number;
  matchedProductId: number | null;
  matchedProductName: string | null;
  confidence: number;
}

// --- Paginated Response ---
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// --- API Error ---
export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: { field: string; message: string; rejectedValue: any }[];
}

// --- Dashboard ---
export interface DashboardStats {
  todayOrders: number;
  todayRevenue: number;
  pendingOrders: number;
  monthlyRevenue: number;
  lowStockProducts: number;
}
