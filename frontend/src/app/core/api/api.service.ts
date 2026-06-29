import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Product, Order, Customer, Page, ParsedWhatsAppOrder,
  Tenant, DashboardStats
} from '../models';

/**
 * Centralized API service for all backend communication.
 * All requests go through the AuthInterceptor for JWT attachment.
 */
@Injectable({
  providedIn: 'root'
})
export class ApiService {

  private readonly API = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // --- Products ---
  getProducts(page = 0, size = 20, category?: string): Observable<Page<Product>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (category) params = params.set('category', category);
    return this.http.get<Page<Product>>(`${this.API}/products`, { params });
  }

  getProduct(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.API}/products/${id}`);
  }

  searchProducts(query: string): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.API}/products/search`, {
      params: new HttpParams().set('q', query)
    });
  }

  getCategories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.API}/products/categories`);
  }

  createProduct(product: Partial<Product>): Observable<Product> {
    return this.http.post<Product>(`${this.API}/products`, product);
  }

  updateProduct(id: number, product: Partial<Product>): Observable<Product> {
    return this.http.put<Product>(`${this.API}/products/${id}`, product);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/products/${id}`);
  }

  getLowStockProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.API}/products/low-stock`);
  }

  // --- Orders ---
  getOrders(page = 0, size = 20, filters?: {
    status?: string; from?: string; to?: string; salespersonId?: number
  }): Observable<Page<Order>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.from) params = params.set('from', filters.from);
    if (filters?.to) params = params.set('to', filters.to);
    if (filters?.salespersonId) params = params.set('salespersonId', filters.salespersonId);
    return this.http.get<Page<Order>>(`${this.API}/orders`, { params });
  }

  getOrder(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.API}/orders/${id}`);
  }

  createOrder(payload: { order: Partial<Order>; items: Partial<any>[] }): Observable<Order> {
    return this.http.post<Order>(`${this.API}/orders`, payload);
  }

  createManualOrder(payload: any): Observable<any> {
    return this.http.post(`${this.API}/orders/manual`, payload);
  }

  updateOrderStatus(orderId: number, status: string, notes?: string): Observable<Order> {
    return this.http.patch<Order>(`${this.API}/orders/${orderId}/status`, { status, notes });
  }

  parseWhatsApp(text: string): Observable<ParsedWhatsAppOrder> {
    return this.http.post<ParsedWhatsAppOrder>(`${this.API}/orders/parse-whatsapp`, { text });
  }

  // --- Customers ---
  getCustomers(page = 0, size = 20): Observable<Page<Customer>> {
    return this.http.get<Page<Customer>>(`${this.API}/customers`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  searchCustomers(query: string): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.API}/customers/search`, {
      params: new HttpParams().set('q', query)
    });
  }

  createCustomer(customer: Partial<Customer>): Observable<Customer> {
    return this.http.post<Customer>(`${this.API}/customers`, customer);
  }

  updateCustomer(id: number, customer: Partial<Customer>): Observable<Customer> {
    return this.http.put<Customer>(`${this.API}/customers/${id}`, customer);
  }

  // --- Dispatch ---
  getDispatchQueue(page = 0, size = 20): Observable<Page<Order>> {
    return this.http.get<Page<Order>>(`${this.API}/dispatch/queue`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  generateLabels(orderIds: number[]): Observable<Blob> {
    return this.http.post(`${this.API}/dispatch/generate-labels`, { orderIds }, {
      responseType: 'blob'
    });
  }

  // --- Reports ---
  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.API}/reports/dashboard`);
  }

  getDailyReport(date: string): Observable<any> {
    return this.http.get(`${this.API}/reports/daily`, {
      params: new HttpParams().set('date', date)
    });
  }

  getMonthlyReport(month: number, year: number): Observable<any> {
    return this.http.get(`${this.API}/reports/monthly`, {
      params: new HttpParams().set('month', month).set('year', year)
    });
  }

  getSalespersonPerformance(startDate: string, endDate: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.API}/reports/salesperson-performance`, {
      params: new HttpParams().set('startDate', startDate).set('endDate', endDate)
    });
  }

  getTopProducts(startDate: string, endDate: string, limit = 10): Observable<any> {
    return this.http.get<any>(`${this.API}/reports/top-products`, {
      params: new HttpParams().set('startDate', startDate).set('endDate', endDate).set('limit', limit)
    });
  }

  getCustomerAnalytics(startDate: string, endDate: string): Observable<any> {
    return this.http.get<any>(`${this.API}/reports/customer-analytics`, {
      params: new HttpParams().set('startDate', startDate).set('endDate', endDate)
    });
  }

  // --- Billing ---
  exportVyapar(dateFrom: string, dateTo: string, format: string): Observable<Blob> {
    return this.http.post(`${this.API}/billing/export/vyapar`, { dateFrom, dateTo, format }, {
      responseType: 'blob'
    });
  }

  // --- Super Admin ---
  getTenants(): Observable<Tenant[]> {
    return this.http.get<Tenant[]>(`${this.API}/admin/tenants`);
  }

  onboardTenant(data: any): Observable<Tenant> {
    return this.http.post<Tenant>(`${this.API}/admin/tenants`, data);
  }

  updateTenantStatus(id: number, status: string): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.API}/admin/tenants/${id}/status`, { status });
  }

  getPlatformAnalytics(): Observable<any> {
    return this.http.get<any>(`${this.API}/admin/tenants/analytics`);
  }

  validateDatabaseConnection(dbUrl: string, dbUsername: string, dbPassword: string): Observable<any> {
    return this.http.post<any>(`${this.API}/admin/tenants/validate-connection`, {
      dbUrl, dbUsername, dbPassword
    });
  }

  getTenantUsers(tenantId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.API}/admin/tenants/${tenantId}/users`);
  }

  // --- User Management ---
  getUsers(page = 0, size = 50): Observable<any> {
    return this.http.get<any>(`${this.API}/admin/users`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  createUser(user: any): Observable<any> {
    return this.http.post<any>(`${this.API}/admin/users`, user);
  }

  updateUser(id: number, user: any): Observable<any> {
    return this.http.put<any>(`${this.API}/admin/users/${id}`, user);
  }

  updateUserStatus(id: number, isActive: boolean): Observable<void> {
    return this.http.patch<void>(`${this.API}/admin/users/${id}/status`, { isActive });
  }

  // --- Salesperson Targets ---
  setTarget(salespersonId: number, month: number, year: number, targetAmount: number): Observable<any> {
    return this.http.post<any>(`${this.API}/salesperson/targets`, {
      salespersonId, month, year, targetAmount
    });
  }

  getTarget(salespersonId: number, month: number, year: number): Observable<any> {
    return this.http.get<any>(`${this.API}/salesperson/targets`, {
      params: new HttpParams()
        .set('salespersonId', salespersonId)
        .set('month', month)
        .set('year', year)
    });
  }

  getAllTargets(salespersonId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.API}/salesperson/${salespersonId}/targets`);
  }

  getAchievementSummary(salespersonId: number, month: number, year: number): Observable<any> {
    return this.http.get<any>(`${this.API}/salesperson/${salespersonId}/achievement`, {
      params: new HttpParams().set('month', month).set('year', year)
    });
  }

  getYearlyAchievements(salespersonId: number, year: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.API}/salesperson/${salespersonId}/yearly-achievements`, {
      params: new HttpParams().set('year', year)
    });
  }

  recalculateTargets(month: number, year: number): Observable<any> {
    return this.http.post<any>(`${this.API}/salesperson/targets/recalculate`, null, {
      params: new HttpParams().set('month', month).set('year', year)
    });
  }

  getMonthTargets(month: number, year: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.API}/salesperson/targets/month`, {
      params: new HttpParams().set('month', month).set('year', year)
    });
  }

  deleteTarget(targetId: number): Observable<any> {
    return this.http.delete<any>(`${this.API}/salesperson/targets/${targetId}`);
  }

  // --- Coupons ---
  getCoupons(): Observable<any[]> {
    return this.http.get<any[]>(`${this.API}/coupons`);
  }

  createCoupon(coupon: any): Observable<any> {
    return this.http.post<any>(`${this.API}/coupons`, coupon);
  }

  toggleCoupon(id: number): Observable<any> {
    return this.http.patch<any>(`${this.API}/coupons/${id}/toggle`, {});
  }

  deleteCoupon(id: number): Observable<any> {
    return this.http.delete<any>(`${this.API}/coupons/${id}`);
  }
}
