import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { StorefrontApiService } from '../../../services/storefront-api.service';
import { StorefrontAuthService, SfUser } from '../../../services/storefront-auth.service';
import { CartService } from '../../../services/cart.service';
import { WishlistService } from '../../../services/wishlist.service';
import { Product } from '../../../models/product.model';
import { StorefrontConfig } from '../../../models/storefront-config.model';
import { IconComponent } from '../../../shared/ui/icon.component';

@Component({
  selector: 'app-storefront-landing',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  templateUrl: './storefront-landing.component.html',
  styleUrls: ['./storefront-landing.component.scss']
})
export class StorefrontLandingComponent implements OnInit {
  config: StorefrontConfig | null = null;
  featuredProducts: Product[] = [];
  categories: string[] = [];
  loading = true;
  cartCount = 0;
  wishlistCount = 0;
  currentUser: SfUser | null = null;
  isLoggedIn = false;

  // SVG placeholder for missing images
  placeholderImage = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjQwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjQwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTgiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5Qcm9kdWN0IEltYWdlPC90ZXh0Pjwvc3ZnPg==';

  constructor(
    private storefrontApi: StorefrontApiService,
    private sfAuth: StorefrontAuthService,
    private cartService: CartService,
    private wishlistService: WishlistService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadStorefrontData();
    this.subscribeToCart();
    this.subscribeToWishlist();
    this.sfAuth.user$.subscribe(user => {
      this.currentUser = user;
      this.isLoggedIn = !!user;
    });
  }

  loadStorefrontData(): void {
    // Load configuration
    this.storefrontApi.getConfig().subscribe({
      next: (config) => {
        this.config = config;
        this.applyBranding(config);
      },
      error: (error) => console.error('Error loading config:', error)
    });

    // Load featured products
    this.storefrontApi.getFeaturedProducts(12).subscribe({
      next: (products) => {
        this.featuredProducts = products;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading products:', error);
        this.loading = false;
      }
    });

    // Load categories
    this.storefrontApi.getCategories().subscribe({
      next: (categories) => {
        this.categories = categories;
      },
      error: (error) => console.error('Error loading categories:', error)
    });
  }

  subscribeToCart(): void {
    this.cartService.cart$.subscribe(cart => {
      this.cartCount = cart.itemCount;
    });
  }

  subscribeToWishlist(): void {
    this.wishlistService.wishlist$.subscribe(wishlist => {
      this.wishlistCount = wishlist.length;
    });
  }

  applyBranding(config: StorefrontConfig): void {
    if (config.primaryColor) {
      document.documentElement.style.setProperty('--primary-color', config.primaryColor);
    }
    if (config.secondaryColor) {
      document.documentElement.style.setProperty('--secondary-color', config.secondaryColor);
    }
    if (config.accentColor) {
      document.documentElement.style.setProperty('--accent-color', config.accentColor);
    }
  }

  addToCart(product: Product, event: Event): void {
    event.stopPropagation();
    this.cartService.addToCart(product, 1);
  }

  addToWishlist(product: Product, event: Event): void {
    event.stopPropagation();
    this.wishlistService.addToWishlist(product);
  }

  isInWishlist(productId: number): boolean {
    return this.wishlistService.isInWishlist(productId);
  }

  getDiscount(product: Product): number {
    if (product.mrp && product.price && product.mrp > product.price) {
      return Math.round(((product.mrp - product.price) / product.mrp) * 100);
    }
    return 0;
  }

  viewProduct(productId: number): void {
    this.router.navigate(['/store/products', productId]);
  }

  browseCategory(category: string): void {
    this.router.navigate(['/store/products'], { queryParams: { category } });
  }

  navigateToCart(): void {
    this.router.navigate(['/store/cart']);
  }

  navigateToWishlist(): void {
    this.router.navigate(['/store/wishlist']);
  }

  navigateToCatalog(): void {
    this.router.navigate(['/store/products']);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src = this.placeholderImage;
  }

  shareOnWhatsApp(product: Product, event: Event): void {
    event.stopPropagation();
    this.storefrontApi.trackShare(product.id).subscribe({ error: () => {} });
    const url = `${window.location.origin}/store/products/${product.id}`;
    const msg = `Check out ${product.name} — ₹${product.price} | ${url}`;
    window.open(`https://wa.me/?text=${encodeURIComponent(msg)}`, '_blank');
  }

  getUserInitials(): string {
    if (!this.currentUser?.fullName) return '?';
    return this.currentUser.fullName.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
  }
}
