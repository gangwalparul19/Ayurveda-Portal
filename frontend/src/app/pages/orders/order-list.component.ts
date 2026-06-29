import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { Order, OrderStatus, Page } from '../../core/models';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './order-list.component.html',
  styleUrls: ['./order-list.component.scss']
})
export class OrderListComponent implements OnInit {
  orders: Order[] = [];
  totalElements = 0;
  totalPages = 0;
  currentPage = 0;
  pageSize = 20;
  isLoading = true;

  // Filters
  statusFilter: string = '';
  dateFrom = '';
  dateTo = '';

  statuses: OrderStatus[] = ['NEW', 'CONFIRMED', 'PAID', 'PACKED', 'DISPATCHED', 'DELIVERED', 'CANCELLED', 'RETURNED'];

  // Status update
  showStatusModal = false;
  selectedOrder: Order | null = null;
  newStatus = '';
  statusNotes = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.isLoading = true;
    const filters: any = {};
    if (this.statusFilter) filters.status = this.statusFilter;
    if (this.dateFrom) filters.from = this.dateFrom;
    if (this.dateTo) filters.to = this.dateTo;

    this.api.getOrders(this.currentPage, this.pageSize, filters).subscribe({
      next: (page: Page<Order>) => {
        this.orders = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = page.totalPages;
        this.isLoading = false;
      },
      error: () => { this.isLoading = false; }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadOrders();
  }

  clearFilters(): void {
    this.statusFilter = '';
    this.dateFrom = '';
    this.dateTo = '';
    this.currentPage = 0;
    this.loadOrders();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadOrders();
    }
  }

  openStatusModal(order: Order): void {
    this.selectedOrder = order;
    this.newStatus = '';
    this.statusNotes = '';
    this.showStatusModal = true;
  }

  closeStatusModal(): void {
    this.showStatusModal = false;
    this.selectedOrder = null;
  }

  updateStatus(): void {
    if (!this.selectedOrder || !this.newStatus) return;
    this.api.updateOrderStatus(this.selectedOrder.id, this.newStatus, this.statusNotes).subscribe({
      next: () => {
        this.closeStatusModal();
        this.loadOrders();
      }
    });
  }

  getStatusClass(status: string): string {
    return 'badge badge-' + status.toLowerCase();
  }

  getPaymentClass(status: string): string {
    switch (status) {
      case 'PAID': return 'badge badge-paid';
      case 'PENDING': return 'badge badge-pending';
      case 'PARTIAL': return 'badge badge-packed';
      case 'REFUNDED': return 'badge badge-cancelled';
      default: return 'badge';
    }
  }

  getNextStatuses(currentStatus: string): string[] {
    const flow: Record<string, string[]> = {
      'NEW': ['CONFIRMED', 'CANCELLED'],
      'CONFIRMED': ['PAID', 'CANCELLED'],
      'PAID': ['PACKED', 'CANCELLED'],
      'PACKED': ['DISPATCHED', 'CANCELLED'],
      'DISPATCHED': ['DELIVERED', 'RETURNED'],
      'DELIVERED': ['RETURNED'],
      'CANCELLED': [],
      'RETURNED': []
    };
    return flow[currentStatus] || [];
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    const start = Math.max(0, this.currentPage - 2);
    const end = Math.min(this.totalPages, start + 5);
    for (let i = start; i < end; i++) pages.push(i);
    return pages;
  }

  get hasActiveFilters(): boolean {
    return !!(this.statusFilter || this.dateFrom || this.dateTo);
  }
}
