import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { ParsedWhatsAppOrder, ParsedItem, Product } from '../../core/models';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-whatsapp-import',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './whatsapp-import.component.html',
  styleUrls: ['./whatsapp-import.component.scss']
})
export class WhatsAppImportComponent {
  // Step 1: Paste
  rawText = '';
  isParsing = false;

  // Step 2: Review
  parsedOrder: ParsedWhatsAppOrder | null = null;
  productMatches: Map<number, Product[]> = new Map();
  selectedProducts: Map<number, number> = new Map(); // itemIndex -> productId

  // Step 3: Confirm
  isCreating = false;
  errorMessage = '';
  currentStep = 1;

  constructor(
    private api: ApiService,
    private router: Router
  ) {}

  parseText(): void {
    if (!this.rawText.trim()) {
      this.errorMessage = 'Please paste the WhatsApp message text';
      return;
    }
    this.isParsing = true;
    this.errorMessage = '';

    this.api.parseWhatsApp(this.rawText).subscribe({
      next: (parsed) => {
        this.parsedOrder = parsed;
        this.isParsing = false;
        if (parsed.items.length > 0) {
          this.currentStep = 2;
          this.matchProducts();
        } else {
          this.errorMessage = 'No products could be extracted from the text. Try reformatting.';
        }
      },
      error: () => {
        this.isParsing = false;
        this.errorMessage = 'Failed to parse the text. Please try again.';
      }
    });
  }

  matchProducts(): void {
    if (!this.parsedOrder) return;
    this.parsedOrder.items.forEach((item, index) => {
      this.api.searchProducts(item.rawText).subscribe({
        next: (products) => {
          this.productMatches.set(index, products);
          if (products.length > 0) {
            this.selectedProducts.set(index, products[0].id);
          }
        }
      });
    });
  }

  selectProduct(itemIndex: number, productId: number): void {
    this.selectedProducts.set(itemIndex, productId);
  }

  getMatchedProducts(index: number): Product[] {
    return this.productMatches.get(index) || [];
  }

  getSelectedProductId(index: number): number | undefined {
    return this.selectedProducts.get(index);
  }

  goToConfirm(): void {
    this.currentStep = 3;
  }

  createOrder(): void {
    if (!this.parsedOrder) return;
    this.isCreating = true;
    this.errorMessage = '';

    const items = this.parsedOrder.items
      .filter((_, i) => this.selectedProducts.has(i))
      .map((item, i) => ({
        product: { id: this.selectedProducts.get(i) },
        quantity: item.quantity,
        unitPrice: null // Will be filled by backend from product data
      }));

    const order = {
      orderSource: 'WHATSAPP' as const,
      rawWhatsappText: this.rawText,
      paymentMode: 'COD' as const,
      notes: this.parsedOrder.customer?.name
        ? `WhatsApp order from ${this.parsedOrder.customer.name}`
        : 'WhatsApp order'
    };

    this.api.createOrder({ order, items }).subscribe({
      next: (created) => {
        this.isCreating = false;
        this.router.navigate(['/orders', created.id]);
      },
      error: (err) => {
        this.isCreating = false;
        this.errorMessage = err.error?.message || 'Failed to create order';
      }
    });
  }

  goBack(): void {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }

  reset(): void {
    this.rawText = '';
    this.parsedOrder = null;
    this.productMatches.clear();
    this.selectedProducts.clear();
    this.currentStep = 1;
    this.errorMessage = '';
  }
}
