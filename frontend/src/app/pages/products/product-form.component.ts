import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { Product } from '../../core/models';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './product-form.component.html',
  styleUrls: ['./product-form.component.scss']
})
export class ProductFormComponent implements OnInit {
  isEditMode = false;
  productId: number | null = null;
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  successMessage = '';

  product: Partial<Product> = {
    sku: '',
    name: '',
    description: '',
    category: '',
    mrp: 0,
    salePrice: 0,
    unit: 'pcs',
    weightGrams: 0,
    hsnCode: '',
    gstRate: 0,
    imageUrl: '',
    stockQuantity: 0,
    lowStockThreshold: 10
  };

  categories: string[] = [];
  newCategory = '';
  showNewCategory = false;

  units = ['pcs', 'ml', 'gm', 'kg', 'ltr', 'tab', 'cap', 'pack', 'box', 'bottle'];

  constructor(
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadCategories();
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.isEditMode = true;
      this.productId = parseInt(id, 10);
      this.loadProduct();
    }
  }

  loadProduct(): void {
    if (!this.productId) return;
    this.isLoading = true;
    this.api.getProduct(this.productId).subscribe({
      next: (product) => {
        this.product = { ...product };
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = 'Failed to load product';
        this.isLoading = false;
      }
    });
  }

  loadCategories(): void {
    this.api.getCategories().subscribe({
      next: (cats) => { this.categories = cats; }
    });
  }

  onSubmit(): void {
    if (!this.validate()) return;

    this.isSaving = true;
    this.errorMessage = '';

    if (this.showNewCategory && this.newCategory.trim()) {
      this.product.category = this.newCategory.trim();
    }

    const request = this.isEditMode
      ? this.api.updateProduct(this.productId!, this.product)
      : this.api.createProduct(this.product);

    request.subscribe({
      next: (saved) => {
        this.isSaving = false;
        this.successMessage = this.isEditMode ? 'Product updated successfully!' : 'Product created successfully!';
        setTimeout(() => {
          this.router.navigate(['/products']);
        }, 1200);
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || 'Failed to save product. Please check all fields.';
      }
    });
  }

  validate(): boolean {
    if (!this.product.sku?.trim()) {
      this.errorMessage = 'SKU is required';
      return false;
    }
    if (!this.product.name?.trim()) {
      this.errorMessage = 'Product name is required';
      return false;
    }
    if (!this.product.mrp || this.product.mrp <= 0) {
      this.errorMessage = 'MRP must be greater than 0';
      return false;
    }
    if (!this.product.salePrice || this.product.salePrice <= 0) {
      this.errorMessage = 'Sale price must be greater than 0';
      return false;
    }
    return true;
  }

  toggleNewCategory(): void {
    this.showNewCategory = !this.showNewCategory;
    if (!this.showNewCategory) {
      this.newCategory = '';
    }
  }

  autoFillSalePrice(): void {
    if (this.product.mrp && (!this.product.salePrice || this.product.salePrice === 0)) {
      this.product.salePrice = this.product.mrp;
    }
  }
}
