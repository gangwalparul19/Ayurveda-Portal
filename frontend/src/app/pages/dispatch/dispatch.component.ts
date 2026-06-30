import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { Order, Page } from '../../core/models';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-dispatch',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './dispatch.component.html',
  styleUrls: ['./dispatch.component.scss']
})
export class DispatchComponent implements OnInit {
  orders: Order[] = [];
  totalElements = 0;
  totalPages = 0;
  currentPage = 0;
  pageSize = 20;
  isLoading = true;

  // Selection
  selectedOrderIds = new Set<number>();
  selectAll = false;

  // Label generation
  courierPartner = '';
  isGenerating = false;
  isDispatching = false;
  successMessage = '';
  errorMessage = '';

  couriers = ['Delhivery', 'BlueDart', 'DTDC', 'Ecom Express', 'Xpressbees', 'India Post', 'Other'];

  constructor(private api: ApiService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadQueue();
  }

  loadQueue(): void {
    this.isLoading = true;
    this.api.getDispatchQueue(this.currentPage, this.pageSize).subscribe({
      next: (page: Page<Order>) => {
        this.orders = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = page.totalPages;
        this.isLoading = false;
      },
      error: () => { this.isLoading = false; }
    });
  }

  toggleSelect(orderId: number): void {
    if (this.selectedOrderIds.has(orderId)) {
      this.selectedOrderIds.delete(orderId);
    } else {
      this.selectedOrderIds.add(orderId);
    }
    this.selectAll = this.selectedOrderIds.size === this.orders.length;
  }

  toggleSelectAll(): void {
    if (this.selectAll) {
      this.selectedOrderIds.clear();
      this.selectAll = false;
    } else {
      this.orders.forEach(o => this.selectedOrderIds.add(o.id));
      this.selectAll = true;
    }
  }

  isSelected(orderId: number): boolean {
    return this.selectedOrderIds.has(orderId);
  }

  generateLabels(): void {
    if (this.selectedOrderIds.size === 0) {
      this.errorMessage = 'Select at least one order';
      return;
    }
    this.isGenerating = true;
    this.errorMessage = '';
    this.successMessage = '';

    const orderIds = Array.from(this.selectedOrderIds);
    this.api.generateLabels(orderIds).subscribe({
      next: (blob) => {
        this.isGenerating = false;
        this.successMessage = `Labels generated for ${orderIds.length} order(s)!`;
        this.selectedOrderIds.clear();
        this.selectAll = false;
        this.loadQueue();
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = 'Failed to generate labels';
      }
    });
  }

  markDispatched(): void {
    if (this.selectedOrderIds.size === 0) {
      this.errorMessage = 'Select at least one order';
      return;
    }
    this.isDispatching = true;
    this.errorMessage = '';

    const orderIds = Array.from(this.selectedOrderIds);
    // Using the API service to call mark-dispatched
    const body = { orderIds };
    this.api['http'].post(`${this.api['API']}/dispatch/mark-dispatched`, body).subscribe({
      next: () => {
        this.isDispatching = false;
        this.successMessage = `${orderIds.length} order(s) marked as dispatched!`;
        this.selectedOrderIds.clear();
        this.selectAll = false;
        this.loadQueue();
      },
      error: () => {
        this.isDispatching = false;
        this.errorMessage = 'Failed to mark as dispatched';
      }
    });
  }

  getStatusClass(status: string): string {
    return 'badge badge-' + status.toLowerCase();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadQueue();
    }
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    const start = Math.max(0, this.currentPage - 2);
    const end = Math.min(this.totalPages, start + 5);
    for (let i = start; i < end; i++) pages.push(i);
    return pages;
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
