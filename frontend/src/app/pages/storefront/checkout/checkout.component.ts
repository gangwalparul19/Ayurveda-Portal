import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CartService } from '../../../services/cart.service';
import { StorefrontApiService } from '../../../services/storefront-api.service';
import { CartItem, OrderRequest } from '../../../models/cart.model';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {
  cartItems: CartItem[] = [];

  // Customer form data
  customerData = {
    name: '',
    phone: '',
    email: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    state: '',
    pincode: '',
    notes: ''
  };

  // Payment method
  paymentMethod: 'COD' | 'ONLINE' = 'COD';
  razorpayKeyId = '';

  // Coupon state
  couponCode = '';
  couponApplied = false;
  couponDiscount = 0;
  couponId: number | null = null;
  couponError = '';
  couponLoading = false;

  submitting = false;
  orderPlaced = false;
  orderId: number | null = null;
  orderPaymentMethod: 'COD' | 'ONLINE' = 'COD';

  private readonly API = 'http://localhost:8080/api';

  constructor(
    private cartService: CartService,
    private storefrontApi: StorefrontApiService,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.cartItems = this.cartService.getCart().items;
    if (this.cartItems.length === 0) {
      this.router.navigate(['/store/products']);
    }
  }

  getSubtotal(): number {
    return this.cartItems.reduce((sum, item) => sum + (item.product.price * item.quantity), 0);
  }

  getShipping(): number {
    return this.getSubtotal() >= 500 ? 0 : 50;
  }

  getTotal(): number {
    return this.getSubtotal() + this.getShipping() - this.couponDiscount;
  }

  getTotalItems(): number {
    return this.cartItems.reduce((sum, item) => sum + item.quantity, 0);
  }

  // ── Coupon ─────────────────────────────────────────────────────────

  applyCoupon(): void {
    if (!this.couponCode.trim()) {
      this.couponError = 'Please enter a coupon code';
      return;
    }

    this.couponLoading = true;
    this.couponError = '';

    this.http.post<any>(`${this.API}/storefront/coupon/validate`, {
      code: this.couponCode.trim(),
      orderAmount: this.getSubtotal() + this.getShipping(),
      customerPhone: this.customerData.phone
    }).subscribe({
      next: (res) => {
        this.couponLoading = false;
        if (res.valid) {
          this.couponDiscount = res.discountAmount ?? 0;
          this.couponId = res.couponId ?? null;
          this.couponApplied = true;
          this.couponError = '';
        } else {
          this.couponError = res.message || 'Invalid coupon code';
        }
      },
      error: () => {
        this.couponLoading = false;
        this.couponError = 'Unable to validate coupon';
      }
    });
  }

  removeCoupon(): void {
    this.couponCode = '';
    this.couponApplied = false;
    this.couponDiscount = 0;
    this.couponId = null;
    this.couponError = '';
  }

  // ── Razorpay ───────────────────────────────────────────────────────

  private loadRazorpayScript(): Promise<void> {
    return new Promise(resolve => {
      if ((window as any).Razorpay) { resolve(); return; }
      const s = document.createElement('script');
      s.src = 'https://checkout.razorpay.com/v1/checkout.js';
      s.onload = () => resolve();
      document.body.appendChild(s);
    });
  }

  private verifyPayment(response: any, internalOrderId: number): void {
    this.http.post<any>(`${this.API}/storefront/payment/verify`, {
      razorpayOrderId: response.razorpay_order_id,
      razorpayPaymentId: response.razorpay_payment_id,
      razorpaySignature: response.razorpay_signature,
      orderId: internalOrderId
    }).subscribe({
      next: (res) => {
        if (res.success) {
          this.orderId = internalOrderId;
          this.orderPaymentMethod = 'ONLINE';
          this.orderPlaced = true;
          this.cartService.clearCart();
          this.submitting = false;
        } else {
          alert('Payment verification failed: ' + (res.message || 'Please contact support'));
          this.submitting = false;
        }
      },
      error: () => {
        alert('Payment verification failed. Please contact support.');
        this.submitting = false;
      }
    });
  }

  // ── Place Order ────────────────────────────────────────────────────

  placeOrder(): void {
    if (!this.validateForm()) {
      alert('Please fill in all required fields');
      return;
    }

    this.submitting = true;

    const orderRequest: OrderRequest = {
      customerName: this.customerData.name,
      customerPhone: this.customerData.phone,
      customerEmail: this.customerData.email || undefined,
      deliveryAddress: this.customerData.addressLine1 +
        (this.customerData.addressLine2 ? ', ' + this.customerData.addressLine2 : ''),
      city: this.customerData.city,
      state: this.customerData.state,
      pincode: this.customerData.pincode,
      notes: this.customerData.notes || undefined,
      paymentMethod: this.paymentMethod,
      items: this.cartItems.map(item => ({
        productId: item.product.id,
        productName: item.product.name,
        quantity: item.quantity,
        price: item.product.price
      })),
      couponCode: this.couponApplied ? this.couponCode : undefined,
      couponDiscount: this.couponApplied ? this.couponDiscount : undefined
    };

    // Always create the internal order first
    this.storefrontApi.placeOrder(orderRequest).subscribe({
      next: (orderResponse) => {
        const internalOrderId = orderResponse.id;

        if (this.paymentMethod === 'COD') {
          this.orderId = internalOrderId;
          this.orderPaymentMethod = 'COD';
          this.orderPlaced = true;
          this.cartService.clearCart();
          this.submitting = false;
        } else {
          // ONLINE: create Razorpay order and open popup
          this.http.post<any>(`${this.API}/storefront/payment/create-order`, {
            amount: this.getTotal(),
            customerPhone: this.customerData.phone
          }).subscribe({
            next: async (payRes) => {
              this.razorpayKeyId = payRes.keyId;
              await this.loadRazorpayScript();

              const rzp = new (window as any).Razorpay({
                key: payRes.keyId,
                amount: payRes.amount,
                currency: payRes.currency || 'INR',
                name: 'Shifa Ayurveda',
                description: 'Order Payment',
                order_id: payRes.razorpayOrderId,
                prefill: {
                  name: this.customerData.name,
                  contact: this.customerData.phone,
                  email: this.customerData.email || ''
                },
                theme: { color: '#2C5F2E' },
                handler: (response: any) => {
                  this.verifyPayment(response, internalOrderId);
                },
                modal: {
                  ondismiss: () => { this.submitting = false; }
                }
              });
              rzp.open();
            },
            error: () => {
              alert('Failed to initiate online payment. Please try COD or contact support.');
              this.submitting = false;
            }
          });
        }
      },
      error: (error) => {
        console.error('Error placing order:', error);
        alert('Failed to place order. Please try again.');
        this.submitting = false;
      }
    });
  }

  validateForm(): boolean {
    return !!(
      this.customerData.name &&
      this.customerData.phone &&
      this.customerData.addressLine1 &&
      this.customerData.city &&
      this.customerData.state &&
      this.customerData.pincode
    );
  }

  continueShopping(): void {
    this.router.navigate(['/store/products']);
  }

  goToCart(): void {
    this.router.navigate(['/store/cart']);
  }
}
