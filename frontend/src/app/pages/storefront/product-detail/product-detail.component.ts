import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StorefrontApiService } from '../../../services/storefront-api.service';
import { StorefrontAuthService } from '../../../services/storefront-auth.service';
import { CartService } from '../../../services/cart.service';
import { WishlistService } from '../../../services/wishlist.service';
import { Product } from '../../../models/product.model';
import { IconComponent } from '../../../shared/ui/icon.component';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, IconComponent],
  templateUrl: './product-detail.component.html',
  styleUrls: ['./product-detail.component.scss']
})
export class ProductDetailComponent implements OnInit {
  product: Product | null = null;
  quantity = 1;
  loading = true;
  error = false;

  // Reviews
  reviews: any[] = [];
  reviewsLoading = false;
  reviewTotalCount = 0;
  reviewAverageRating = 0;
  reviewTotalPages = 0;
  reviewPage = 0;
  starCounts: number[] = [0, 0, 0, 0, 0];

  // Review form
  reviewerName = '';
  reviewRating = 0;
  reviewTitle = '';
  reviewText = '';
  reviewSubmitting = false;
  reviewSubmitted = false;
  reviewError = '';

  // SVG placeholder for missing images
  placeholderImage = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjQwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjQwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTgiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5Qcm9kdWN0IEltYWdlPC90ZXh0Pjwvc3ZnPg==';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private storefrontApi: StorefrontApiService,
    private sfAuth: StorefrontAuthService,
    private cartService: CartService,
    private wishlistService: WishlistService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const productId = +params['id'];
      if (productId) {
        this.loadProduct(productId);
      }
    });
    const user = this.sfAuth.getCurrentUser();
    if (user) {
      this.reviewerName = user.fullName;
    }
  }

  loadProduct(id: number): void {
    this.loading = true;
    this.error = false;

    this.storefrontApi.getProduct(id).subscribe({
      next: (product) => {
        this.product = product;
        this.loading = false;
        this.storefrontApi.trackView(id);
        this.loadReviews(id);
      },
      error: () => {
        this.error = true;
        this.loading = false;
      }
    });
  }

  loadReviews(productId: number, page = 0): void {
    this.reviewsLoading = true;
    this.storefrontApi.getReviews(productId, page).subscribe({
      next: (data: any) => {
        this.reviews = data.reviews || data.content || [];
        this.reviewTotalCount = data.totalCount || data.totalElements || this.reviews.length;
        this.reviewAverageRating = data.averageRating || 0;
        this.reviewTotalPages = data.totalPages || 1;
        this.reviewPage = page;
        this.computeStarCounts();
        this.reviewsLoading = false;
      },
      error: () => { this.reviewsLoading = false; }
    });
  }

  computeStarCounts(): void {
    this.starCounts = [0, 0, 0, 0, 0];
    for (const r of this.reviews) {
      const idx = Math.round(r.rating) - 1;
      if (idx >= 0 && idx < 5) this.starCounts[idx]++;
    }
  }

  getStarCountForRating(star: number): number {
    return this.starCounts[star - 1] || 0;
  }

  getStarPercent(star: number): number {
    if (!this.reviewTotalCount) return 0;
    return (this.getStarCountForRating(star) / this.reviewTotalCount) * 100;
  }

  starsArray(n: number): number[] {
    return Array.from({ length: 5 }, (_, i) => i + 1);
  }

  setReviewRating(r: number): void {
    this.reviewRating = r;
  }

  submitReview(): void {
    if (!this.reviewerName || this.reviewRating === 0 || !this.reviewText) {
      this.reviewError = 'Please provide your name, rating, and review text.';
      return;
    }
    if (!this.product) return;
    this.reviewSubmitting = true;
    this.reviewError = '';
    const token = this.sfAuth.getToken() || undefined;

    this.storefrontApi.submitReview(this.product.id, {
      reviewerName: this.reviewerName,
      rating: this.reviewRating,
      title: this.reviewTitle,
      reviewText: this.reviewText
    }, token).subscribe({
      next: () => {
        this.reviewSubmitting = false;
        this.reviewSubmitted = true;
        this.reviewTitle = '';
        this.reviewText = '';
        this.reviewRating = 0;
      },
      error: (err: any) => {
        this.reviewSubmitting = false;
        this.reviewError = err?.error?.message || 'Could not submit review. Please try again.';
      }
    });
  }

  shareOnWhatsApp(): void {
    if (!this.product) return;
    this.storefrontApi.trackShare(this.product.id).subscribe({ error: () => {} });
    const url = window.location.href;
    const msg = `Check out ${this.product.name} — ₹${this.product.price} | ${url}`;
    window.open(`https://wa.me/?text=${encodeURIComponent(msg)}`, '_blank');
  }

  addToCart(): void {
    if (this.product) {
      this.cartService.addToCart(this.product, this.quantity);
      this.router.navigate(['/store/cart']);
    }
  }

  addToCartAndContinue(): void {
    if (this.product) {
      this.cartService.addToCart(this.product, this.quantity);
    }
  }

  toggleWishlist(): void {
    if (this.product) {
      this.wishlistService.toggleWishlist(this.product);
    }
  }

  isInWishlist(): boolean {
    return this.product ? this.wishlistService.isInWishlist(this.product.id) : false;
  }

  getDiscount(): number {
    if (this.product && this.product.mrp && this.product.price && this.product.mrp > this.product.price) {
      return Math.round(((this.product.mrp - this.product.price) / this.product.mrp) * 100);
    }
    return 0;
  }

  increaseQuantity(): void {
    if (this.product && this.quantity < this.product.stockQuantity) {
      this.quantity++;
    }
  }

  decreaseQuantity(): void {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  goBack(): void {
    this.router.navigate(['/store/products']);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src = this.placeholderImage;
  }

  formatReviewDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
