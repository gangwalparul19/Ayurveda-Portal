import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { StorefrontApiService } from '../../../services/storefront-api.service';
import { StorefrontAuthService } from '../../../services/storefront-auth.service';

@Component({
  selector: 'app-my-orders',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './my-orders.component.html',
  styleUrls: ['./my-orders.component.scss']
})
export class MyOrdersComponent implements OnInit {
  orders: any[] = [];
  loading = true;
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;

  constructor(
    private sfApi: StorefrontApiService,
    private sfAuth: StorefrontAuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.sfAuth.isLoggedIn()) {
      this.router.navigate(['/store/login']);
      return;
    }
    this.loadOrders();
  }

  loadOrders(): void {
    const token = this.sfAuth.getToken();
    if (!token) return;

    this.loading = true;
    this.sfApi.getMyOrders(token, this.currentPage).subscribe({
      next: (page: any) => {
        this.orders = page.content || page || [];
        this.totalPages = page.totalPages || 1;
        this.totalElements = page.totalElements || this.orders.length;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  viewOrder(orderId: number): void {
    this.router.navigate(['/store/orders', orderId]);
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadOrders();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  getStatusClass(status: string): string {
    const s = (status || '').toLowerCase();
    if (s === 'delivered') return 'status-delivered';
    if (s === 'dispatched') return 'status-dispatched';
    if (s === 'packed') return 'status-packed';
    if (s === 'confirmed' || s === 'paid') return 'status-confirmed';
    if (s === 'cancelled') return 'status-cancelled';
    return 'status-new';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  getItemsSummary(order: any): string {
    const items: string[] = order.itemsSummary || [];
    return items.slice(0, 2).join(', ') + (items.length > 2 ? ` +${items.length - 2} more` : '');
  }
}
