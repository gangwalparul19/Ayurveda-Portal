import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { Product, Page } from '../../core/models';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.scss']
})
export class ProductListComponent implements OnInit {
  products: Product[] = [];
  categories: string[] = [];
  totalElements = 0;
  totalPages = 0;
  currentPage = 0;
  pageSize = 20;
  isLoading = true;

  // Filters
  searchQuery = '';
  selectedCategory = '';
  showLowStockOnly = false;

  // Modal state
  showDeleteConfirm = false;
  productToDelete: Product | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadProducts();
    this.loadCategories();
  }

  loadProducts(): void {
    this.isLoading = true;

    if (this.searchQuery.trim()) {
      this.api.searchProducts(this.searchQuery).subscribe({
        next: (products) => {
          this.products = products;
          this.totalElements = products.length;
          this.isLoading = false;
        },
        error: () => { this.isLoading = false; }
      });
    } else if (this.showLowStockOnly) {
      this.api.getLowStockProducts().subscribe({
        next: (products) => {
          this.products = products;
          this.totalElements = products.length;
          this.isLoading = false;
        },
        error: () => { this.isLoading = false; }
      });
    } else {
      this.api.getProducts(this.currentPage, this.pageSize, this.selectedCategory || undefined).subscribe({
        next: (page: Page<Product>) => {
          this.products = page.content;
          this.totalElements = page.totalElements;
          this.totalPages = page.totalPages;
          this.isLoading = false;
        },
        error: () => { this.isLoading = false; }
      });
    }
  }

  loadCategories(): void {
    this.api.getCategories().subscribe({
      next: (cats) => { this.categories = cats; }
    });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.loadProducts();
  }

  onCategoryChange(): void {
    this.currentPage = 0;
    this.searchQuery = '';
    this.loadProducts();
  }

  toggleLowStock(): void {
    this.showLowStockOnly = !this.showLowStockOnly;
    this.currentPage = 0;
    this.selectedCategory = '';
    this.searchQuery = '';
    this.loadProducts();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadProducts();
    }
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.selectedCategory = '';
    this.showLowStockOnly = false;
    this.currentPage = 0;
    this.loadProducts();
  }

  confirmDelete(product: Product): void {
    this.productToDelete = product;
    this.showDeleteConfirm = true;
  }

  deleteProduct(): void {
    if (!this.productToDelete) return;
    this.api.deleteProduct(this.productToDelete.id).subscribe({
      next: () => {
        this.showDeleteConfirm = false;
        this.productToDelete = null;
        this.loadProducts();
      }
    });
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.productToDelete = null;
  }

  getStockClass(product: Product): string {
    if (product.stockQuantity <= 0) return 'stock-out';
    if (product.stockQuantity <= product.lowStockThreshold) return 'stock-low';
    return 'stock-ok';
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    const start = Math.max(0, this.currentPage - 2);
    const end = Math.min(this.totalPages, start + 5);
    for (let i = start; i < end; i++) {
      pages.push(i);
    }
    return pages;
  }
}
