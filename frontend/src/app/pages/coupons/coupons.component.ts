import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';

@Component({
  selector: 'app-coupons',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './coupons.component.html',
  styleUrls: ['./coupons.component.scss']
})
export class CouponsComponent implements OnInit {
  coupons: any[] = [];
  loading = true;
  actionInProgress: number | null = null;
  successMsg = '';
  errorMsg = '';

  // Create form
  showCreateForm = false;
  creating = false;
  newCoupon = this.blankCoupon();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadCoupons();
  }

  loadCoupons(): void {
    this.loading = true;
    this.api.getCoupons().subscribe({
      next: (data) => {
        this.coupons = data;
        this.loading = false;
      },
      error: () => {
        this.errorMsg = 'Failed to load coupons.';
        this.loading = false;
      }
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
    if (!this.showCreateForm) {
      this.newCoupon = this.blankCoupon();
    }
  }

  createCoupon(): void {
    if (!this.newCoupon.code || !this.newCoupon.discountType || !this.newCoupon.discountValue) {
      this.errorMsg = 'Please fill in all required fields (Code, Type, Value).';
      setTimeout(() => (this.errorMsg = ''), 4000);
      return;
    }

    this.creating = true;
    this.api.createCoupon(this.newCoupon).subscribe({
      next: (created) => {
        this.coupons = [created, ...this.coupons];
        this.creating = false;
        this.showCreateForm = false;
        this.newCoupon = this.blankCoupon();
        this.successMsg = 'Coupon created successfully.';
        setTimeout(() => (this.successMsg = ''), 3000);
      },
      error: () => {
        this.errorMsg = 'Failed to create coupon.';
        this.creating = false;
        setTimeout(() => (this.errorMsg = ''), 4000);
      }
    });
  }

  toggleCoupon(coupon: any): void {
    this.actionInProgress = coupon.id;
    this.api.toggleCoupon(coupon.id).subscribe({
      next: (updated) => {
        const idx = this.coupons.findIndex(c => c.id === coupon.id);
        if (idx !== -1) {
          this.coupons[idx] = updated;
          this.coupons = [...this.coupons];
        }
        this.actionInProgress = null;
        this.successMsg = `Coupon ${updated.isActive ? 'activated' : 'deactivated'}.`;
        setTimeout(() => (this.successMsg = ''), 3000);
      },
      error: () => {
        this.errorMsg = 'Failed to toggle coupon.';
        this.actionInProgress = null;
        setTimeout(() => (this.errorMsg = ''), 4000);
      }
    });
  }

  deleteCoupon(coupon: any): void {
    if (!confirm(`Delete coupon "${coupon.code}"? This cannot be undone.`)) return;

    this.actionInProgress = coupon.id;
    this.api.deleteCoupon(coupon.id).subscribe({
      next: () => {
        this.coupons = this.coupons.filter(c => c.id !== coupon.id);
        this.actionInProgress = null;
        this.successMsg = 'Coupon deleted.';
        setTimeout(() => (this.successMsg = ''), 3000);
      },
      error: () => {
        this.errorMsg = 'Failed to delete coupon.';
        this.actionInProgress = null;
        setTimeout(() => (this.errorMsg = ''), 4000);
      }
    });
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  formatDiscount(coupon: any): string {
    if (!coupon) return '—';
    if (coupon.discountType === 'PERCENT') return `${coupon.discountValue}%`;
    return `₹${coupon.discountValue}`;
  }

  private blankCoupon() {
    return {
      code: '',
      description: '',
      discountType: 'PERCENT',
      discountValue: null as number | null,
      minOrderAmount: null as number | null,
      maxDiscount: null as number | null,
      usageLimit: null as number | null,
      validUntil: '',
      isActive: true
    };
  }
}
