import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Product, ProductPage } from '../models/product.model';
import { StorefrontConfig } from '../models/storefront-config.model';
import { OrderRequest } from '../models/cart.model';

@Injectable({
  providedIn: 'root'
})
export class StorefrontApiService {
  private readonly API_URL = `${environment.apiUrl}/storefront`;

  constructor(private http: HttpClient) {}

  /**
   * Get storefront configuration (branding, colors, etc.)
   */
  getConfig(): Observable<StorefrontConfig> {
    return this.http.get<StorefrontConfig>(`${this.API_URL}/config`);
  }

  /**
   * Get paginated products
   */
  getProducts(page: number = 0, size: number = 20, sortBy: string = 'name', 
              sortDir: string = 'asc', category?: string): Observable<ProductPage> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    
    if (category) {
      params = params.set('category', category);
    }

    return this.http.get<ProductPage>(`${this.API_URL}/products`, { params });
  }

  /**
   * Get single product by ID
   */
  getProduct(productId: number): Observable<Product> {
    return this.http.get<Product>(`${this.API_URL}/products/${productId}`);
  }

  /**
   * Search products
   */
  searchProducts(query: string): Observable<Product[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<Product[]>(`${this.API_URL}/products/search`, { params });
  }

  /**
   * Get featured products
   */
  getFeaturedProducts(limit: number = 12): Observable<Product[]> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<Product[]>(`${this.API_URL}/products/featured`, { params });
  }

  /**
   * Get all categories
   */
  getCategories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/categories`);
  }

  /**
   * Place an order
   */
  placeOrder(orderData: OrderRequest): Observable<any> {
    return this.http.post(`${this.API_URL}/orders`, orderData);
  }

  // ── Storefront Auth ────────────────────────────────────────────────────

  private bearerHeaders(token: string): { headers: HttpHeaders } {
    return { headers: new HttpHeaders({ Authorization: `Bearer ${token}` }) };
  }

  getMyOrders(token: string, page = 0): Observable<any> {
    const params = new HttpParams().set('page', page.toString());
    return this.http.get(`${this.API_URL}/my-orders`, {
      params,
      ...this.bearerHeaders(token)
    });
  }

  getMyOrderDetail(token: string, orderId: number): Observable<any> {
    return this.http.get(`${this.API_URL}/my-orders/${orderId}`, this.bearerHeaders(token));
  }

  // ── Reviews ────────────────────────────────────────────────────────────

  getReviews(productId: number, page = 0): Observable<any> {
    const params = new HttpParams().set('page', page.toString());
    return this.http.get(`${this.API_URL}/reviews/${productId}`, { params });
  }

  submitReview(productId: number, review: any, token?: string): Observable<any> {
    const options = token ? this.bearerHeaders(token) : {};
    return this.http.post(`${this.API_URL}/reviews/${productId}`, review, options);
  }

  // ── Product tracking ────────────────────────────────────────────────────

  trackShare(productId: number): Observable<any> {
    return this.http.post(`${this.API_URL}/products/${productId}/share`, {});
  }

  trackView(productId: number): void {
    this.http.post(`${this.API_URL}/products/${productId}/view`, {}).subscribe({ error: () => {} });
  }

  // ── Admin Reviews ──────────────────────────────────────────────────────

  getAdminPendingReviews(adminToken: string, page = 0): Observable<any> {
    const params = new HttpParams().set('page', page.toString());
    return this.http.get(`${environment.apiUrl}/reviews/pending`, {
      params,
      ...this.bearerHeaders(adminToken)
    });
  }

  approveReview(adminToken: string, reviewId: number): Observable<any> {
    return this.http.patch(
      `${environment.apiUrl}/reviews/${reviewId}/approve`,
      {},
      this.bearerHeaders(adminToken)
    );
  }

  deleteReview(adminToken: string, reviewId: number): Observable<any> {
    return this.http.delete(
      `${environment.apiUrl}/reviews/${reviewId}`,
      this.bearerHeaders(adminToken)
    );
  }
}
