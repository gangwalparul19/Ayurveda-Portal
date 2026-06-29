import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { Order } from '../../core/models';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './order-detail.component.html',
  styleUrls: ['./order-detail.component.scss']
})
export class OrderDetailComponent implements OnInit {
  order: Order | null = null;
  isLoading = true;
  errorMessage = '';

  // Status update
  showStatusModal = false;
  newStatus = '';
  statusNotes = '';

  statuses = ['NEW', 'CONFIRMED', 'PAID', 'PACKED', 'DISPATCHED', 'DELIVERED', 'CANCELLED', 'RETURNED'];

  constructor(
    private api: ApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadOrder(parseInt(id, 10));
    }
  }

  loadOrder(id: number): void {
    this.isLoading = true;
    this.api.getOrder(id).subscribe({
      next: (order) => {
        this.order = order;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load order';
        this.isLoading = false;
      }
    });
  }

  getStatusClass(status: string): string {
    return 'badge badge-' + status.toLowerCase();
  }

  getNextStatuses(): string[] {
    if (!this.order) return [];
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
    return flow[this.order.status] || [];
  }

  openStatusModal(): void {
    this.newStatus = '';
    this.statusNotes = '';
    this.showStatusModal = true;
  }

  closeStatusModal(): void {
    this.showStatusModal = false;
  }

  updateStatus(): void {
    if (!this.order || !this.newStatus) return;
    this.api.updateOrderStatus(this.order.id, this.newStatus, this.statusNotes).subscribe({
      next: (updated) => {
        this.order = updated;
        this.closeStatusModal();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to update status';
      }
    });
  }

  getTimelineSteps(): { label: string; icon: string; active: boolean; current: boolean }[] {
    const steps = [
      { key: 'NEW', label: 'New', icon: '📝' },
      { key: 'CONFIRMED', label: 'Confirmed', icon: '✅' },
      { key: 'PAID', label: 'Paid', icon: '💰' },
      { key: 'PACKED', label: 'Packed', icon: '📦' },
      { key: 'DISPATCHED', label: 'Dispatched', icon: '🚚' },
      { key: 'DELIVERED', label: 'Delivered', icon: '🎉' }
    ];

    const currentIndex = steps.findIndex(s => s.key === this.order?.status);

    return steps.map((step, i) => ({
      label: step.label,
      icon: step.icon,
      active: i <= currentIndex,
      current: i === currentIndex
    }));
  }

  isCancelledOrReturned(): boolean {
    return this.order?.status === 'CANCELLED' || this.order?.status === 'RETURNED';
  }
}
