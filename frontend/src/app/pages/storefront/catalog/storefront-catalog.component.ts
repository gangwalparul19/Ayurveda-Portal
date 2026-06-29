import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StorefrontApiService } from '../../../services/storefront-api.service';
import { StorefrontAuthService, SfUser } from '../../../services/storefront-auth.service';
import { CartService } from '../../../services/cart.service';
import { WishlistService } from '../../../services/wishlist.service';
import { Product, ProductPage } from '../../../models/product.model';
import { IconComponent } from '../../../shared/ui/icon.component';

@Component({
  selector: 'app-storefront-catalog',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, IconComponent],
  templateUrl: './storefront-catalog.component.html',
  styleUrls: ['./storefront-catalog.component.scss']
})
export class StorefrontCatalogComponent implements OnInit {
  products: Product[] = [];
  categories: string[] = [];
  
  // Pagination
  currentPage = 0;
  pageSize = 20;
  totalPages = 0;
  totalElements = 0;
  
  // Filters & Sort
  selectedCategory: string | null = null;
  sortBy = 'name';
  sortDir = 'asc';
  searchQuery = '';
  
  loading = true;
  cartCount = 0;
  wishlistCount = 0;
  currentUser: SfUser | null = null;
  isLoggedIn = false;

  // SVG placeholder for missing images
  placeholderImage = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjQwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjQwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTgiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5Qcm9kdWN0IEltYWdlPC90ZXh0Pjwvc3ZnPg==';

  sortOptions = [
    { value: 'name-asc', label: 'Name (A-Z)', sortBy: 'name', sortDir: 'asc' },
    { value: 'name-desc', label: 'Name (Z-A)', sortBy: 'name', sortDir: 'desc' },
    { value: 'price-asc', label: 'Price (Low to High)', sortBy: 'price', sortDir: 'asc' },
    { value: 'price-desc', label: 'Price (High to Low)', sortBy: 'price', sortDir: 'desc' },
    { value: 'newest', label: 'Newest First', sortBy: 'createdAt', sortDir: 'desc' }
  ];

  constructor(
    private storefrontApi: StorefrontApiService,
    private sfAuth: StorefrontAuthService,
    private cartService: CartService,
    private wishlistService: WishlistService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Check for category query param
    this.route.queryParams.subscribe(params => {
      if (params['category']) {
        this.selectedCategory = params['category'];
      }
      this.loadProducts();
    });

    this.loadCategories();
    this.subscribeToCart();
    this.subscribeToWishlist();
    this.sfAuth.user$.subscribe(user => {
      this.currentUser = user;
      this.isLoggedIn = !!user;
    });
  }

  loadProducts(): void {
    this.loading = true;
    this.storefrontApi.getProducts(
      this.currentPage,
      this.pageSize,
      this.sortBy,
      this.sortDir,
      this.selectedCategory || undefined
    ).subscribe({
      next: (page: ProductPage) => {
        this.products = page.content;
        this.totalPages = page.totalPages;
        this.totalElements = page.totalElements;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading products:', error);
        this.loading = false;
      }
    });
  }

  loadCategories(): void {
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

  onSearch(): void {
    if (this.searchQuery.trim()) {
      this.loading = true;
      this.storefrontApi.searchProducts(this.searchQuery).subscribe({
        next: (products) => {
          this.products = products;
          this.totalPages = 1;
          this.totalElements = products.length;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error searching products:', error);
          this.loading = false;
        }
      });
    } else {
      this.loadProducts();
    }
  }

  onCategorySelect(category: string | null): void {
    this.selectedCategory = category;
    this.currentPage = 0;
    this.searchQuery = '';
    this.loadProducts();
  }

  onSortChange(sortValue: string): void {
    const option = this.sortOptions.find(o => o.value === sortValue);
    if (option) {
      this.sortBy = option.sortBy;
      this.sortDir = option.sortDir;
      this.currentPage = 0;
      this.loadProducts();
    }
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadProducts();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
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

  navigateToCart(): void {
    this.router.navigate(['/store/cart']);
  }

  navigateToWishlist(): void {
    this.router.navigate(['/store/wishlist']);
  }

  navigateHome(): void {
    this.router.navigate(['/store']);
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
