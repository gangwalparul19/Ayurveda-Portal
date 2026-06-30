import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { Customer, Product, PaymentMode, PaymentStatus, Page } from '../../core/models';

interface OrderLineItem {
  productId: number | null;
  quantity: number;
  unitPrice: number;
  discount: number;
  taxAmount: number;
}

import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-order-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './order-create.component.html',
  styleUrls: ['./order-create.component.scss']
})
export class OrderCreateComponent implements OnInit {
  // Customer selection
  customerQuery = '';
  customerResults: Customer[] = [];
  selectedCustomer: Customer | null = null;
  isSearchingCustomers = false;

  // Products
  products: Product[] = [];
  isLoadingProducts = false;

  // Line items
  items: OrderLineItem[] = [];

  // Order-level fields
  paymentMode: PaymentMode | '' = '';
  paymentStatus: PaymentStatus = 'PENDING';
  discountAmount = 0;
  taxAmount = 0;
  shippingCharge = 0;
  orderDate = '';
  notes = '';
  salespersonId: number | null = null;

  // Option lists
  paymentModes: PaymentMode[] = ['COD', 'UPI', 'BANK_TRANSFER', 'ONLINE', 'CREDIT'];
  paymentStatuses: PaymentStatus[] = ['PENDING', 'PARTIAL', 'PAID'];

  // State
  isSubmitting = false;
  errorMessage = '';

  constructor(private api: ApiService, private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.orderDate = this.todayIso();
    this.loadInitialCustomers();
    this.loadProducts();
    this.addItem();
  }

  // --- Helpers ---
  private todayIso(): string {
    const now = new Date();
    const offset = now.getTimezoneOffset();
    const local = new Date(now.getTime() - offset * 60000);
    return local.toISOString().slice(0, 10);
  }

  // --- Customer ---
  loadInitialCustomers(): void {
    this.api.getCustomers(0, 50).subscribe({
      next: (page: Page<Customer>) => { this.customerResults = page.content; }
    });
  }

  searchCustomers(): void {
    const q = this.customerQuery.trim();
    if (!q) {
      this.loadInitialCustomers();
      return;
    }
    this.isSearchingCustomers = true;
    this.api.searchCustomers(q).subscribe({
      next: (results) => {
        this.customerResults = results;
        this.isSearchingCustomers = false;
      },
      error: () => { this.isSearchingCustomers = false; }
    });
  }

  selectCustomer(customer: Customer): void {
    this.selectedCustomer = customer;
  }

  clearCustomer(): void {
    this.selectedCustomer = null;
  }

  // --- Products ---
  loadProducts(): void {
    this.isLoadingProducts = true;
    this.api.getProducts(0, 100).subscribe({
      next: (page: Page<Product>) => {
        this.products = page.content;
        this.isLoadingProducts = false;
      },
      error: () => { this.isLoadingProducts = false; }
    });
  }

  getProduct(id: number | null): Product | undefined {
    if (id == null) return undefined;
    return this.products.find(p => p.id === id);
  }

  // --- Line items ---
  addItem(): void {
    this.items.push({ productId: null, quantity: 1, unitPrice: 0, discount: 0, taxAmount: 0 });
  }

  removeItem(index: number): void {
    this.items.splice(index, 1);
  }

  onProductChange(item: OrderLineItem): void {
    const product = this.getProduct(item.productId);
    if (product) {
      item.unitPrice = product.salePrice ?? 0;
    }
  }

  lineTotal(item: OrderLineItem): number {
    const qty = item.quantity || 0;
    const price = item.unitPrice || 0;
    const discount = item.discount || 0;
    const tax = item.taxAmount || 0;
    return qty * price - discount + tax;
  }

  // --- Totals ---
  get subtotal(): number {
    return this.items.reduce((sum, item) => sum + this.lineTotal(item), 0);
  }

  get grandTotal(): number {
    return this.subtotal - (this.discountAmount || 0) + (this.taxAmount || 0) + (this.shippingCharge || 0);
  }

  // --- Validation ---
  get validItems(): OrderLineItem[] {
    return this.items.filter(i => i.productId != null && (i.quantity || 0) >= 1);
  }

  get isValid(): boolean {
    return !!this.selectedCustomer && this.validItems.length > 0 && !!this.paymentMode;
  }

  // --- Submit ---
  submit(): void {
    if (!this.isValid || this.isSubmitting) return;

    this.isSubmitting = true;
    this.errorMessage = '';

    const payload = {
      customerId: this.selectedCustomer!.id,
      salespersonId: this.salespersonId != null ? this.salespersonId : null,
      items: this.validItems.map(i => ({
        productId: i.productId,
        quantity: i.quantity,
        unitPrice: i.unitPrice || 0,
        discount: i.discount || 0,
        taxAmount: i.taxAmount || 0
      })),
      paymentMode: this.paymentMode,
      paymentStatus: this.paymentStatus,
      discountAmount: this.discountAmount || 0,
      taxAmount: this.taxAmount || 0,
      shippingCharge: this.shippingCharge || 0,
      notes: this.notes || '',
      orderDate: this.orderDate
    };

    this.api.createManualOrder(payload).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        this.router.navigate(['/orders', response.id]);
      },
      error: (err) => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Failed to create order. Please review the details and try again.';
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/orders']);
  }

  isSalesperson(): boolean {
    return this.authService.hasRole('SALESPERSON');
  }

  getMaskedPhone(phone?: string): string {
    if (!phone) return '—';
    if (this.isSalesperson() && phone.length >= 4) {
      return '*' + phone.slice(-4);
    }
    return phone;
  }
}
