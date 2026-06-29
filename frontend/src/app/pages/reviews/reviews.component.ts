import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { StorefrontApiService } from '../../services/storefront-api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-reviews',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './reviews.component.html',
  styleUrls: ['./reviews.component.scss']
})
export class ReviewsComponent implements OnInit {
  reviews: any[] = [];
  loading = true;
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  actionInProgress: number | null = null;
  successMsg = '';
  errorMsg = '';

  constructor(
    private sfApi: StorefrontApiService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadReviews();
  }

  loadReviews(): void {
    const token = this.authService.getAccessToken();
    if (!token) return;

    this.loading = true;
    this.sfApi.getAdminPendingReviews(token, this.currentPage).subscribe({
      next: (page: any) => {
        this.reviews = page.content || page || [];
        this.totalPages = page.totalPages || 1;
        this.totalElements = page.totalElements || this.reviews.length;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  approveReview(reviewId: number): void {
    const token = this.authService.getAccessToken();
    if (!token) return;

    this.actionInProgress = reviewId;
    this.sfApi.approveReview(token, reviewId).subscribe({
      next: () => {
        this.actionInProgress = null;
        this.successMsg = 'Review approved.';
        this.reviews = this.reviews.filter(r => r.id !== reviewId && r.reviewId !== reviewId);
        setTimeout(() => (this.successMsg = ''), 3000);
      },
      error: () => {
        this.actionInProgress = null;
        this.errorMsg = 'Failed to approve review.';
        setTimeout(() => (this.errorMsg = ''), 3000);
      }
    });
  }

  deleteReview(reviewId: number): void {
    if (!confirm('Delete this review?')) return;
    const token = this.authService.getAccessToken();
    if (!token) return;

    this.actionInProgress = reviewId;
    this.sfApi.deleteReview(token, reviewId).subscribe({
      next: () => {
        this.actionInProgress = null;
        this.successMsg = 'Review deleted.';
        this.reviews = this.reviews.filter(r => r.id !== reviewId && r.reviewId !== reviewId);
        setTimeout(() => (this.successMsg = ''), 3000);
      },
      error: () => {
        this.actionInProgress = null;
        this.errorMsg = 'Failed to delete review.';
        setTimeout(() => (this.errorMsg = ''), 3000);
      }
    });
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadReviews();
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  getReviewId(r: any): number {
    return r.id ?? r.reviewId;
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
