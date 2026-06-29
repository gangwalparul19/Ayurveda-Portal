import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { StorefrontApiService } from '../../../services/storefront-api.service';
import { StorefrontAuthService } from '../../../services/storefront-auth.service';

const ORDER_STEPS = ['NEW', 'CONFIRMED', 'PAID', 'PACKED', 'DISPATCHED', 'DELIVERED'];

@Component({
  selector: 'app-storefront-order-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './order-detail.component.html',
  styleUrls: ['./order-detail.component.scss']
})
export class StorefrontOrderDetailComponent implements OnInit {
  order: any = null;
  loading = true;
  steps = ORDER_STEPS;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private sfApi: StorefrontApiService,
    private sfAuth: StorefrontAuthService
  ) {}

  ngOnInit(): void {
    if (!this.sfAuth.isLoggedIn()) {
      this.router.navigate(['/store/login']);
      return;
    }
    this.route.params.subscribe(params => {
      const id = +params['id'];
      if (id) this.loadOrder(id);
    });
  }

  loadOrder(id: number): void {
    const token = this.sfAuth.getToken();
    if (!token) return;

    this.loading = true;
    this.sfApi.getMyOrderDetail(token, id).subscribe({
      next: (order: any) => {
        this.order = order;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  getCurrentStepIndex(): number {
    if (!this.order) return 0;
    const idx = ORDER_STEPS.indexOf(this.order.status);
    return idx >= 0 ? idx : 0;
  }

  isStepCompleted(stepIndex: number): boolean {
    return stepIndex <= this.getCurrentStepIndex();
  }

  isStepActive(stepIndex: number): boolean {
    return stepIndex === this.getCurrentStepIndex();
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-IN', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  getSubtotal(): number {
    if (!this.order?.items) return 0;
    return this.order.items.reduce((sum: number, item: any) => sum + (item.unitPrice * item.quantity), 0);
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

  goBack(): void {
    this.router.navigate(['/store/orders']);
  }
}
