import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { Product } from '../models/product.model';
import { Cart, CartItem } from '../models/cart.model';

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private readonly STORAGE_KEY = 'shifa_cart';
  private cartSubject: BehaviorSubject<Cart>;
  public cart$: Observable<Cart>;

  constructor() {
    const savedCart = this.loadCartFromStorage();
    this.cartSubject = new BehaviorSubject<Cart>(savedCart);
    this.cart$ = this.cartSubject.asObservable();
  }

  /**
   * Add product to cart
   */
  addToCart(product: Product, quantity: number = 1): void {
    const currentCart = this.cartSubject.value;
    const existingItem = currentCart.items.find(item => item.product.id === product.id);

    if (existingItem) {
      existingItem.quantity += quantity;
    } else {
      currentCart.items.push({ product, quantity });
    }

    this.updateCart(currentCart);
  }

  /**
   * Remove product from cart
   */
  removeFromCart(productId: number): void {
    const currentCart = this.cartSubject.value;
    currentCart.items = currentCart.items.filter(item => item.product.id !== productId);
    this.updateCart(currentCart);
  }

  /**
   * Update product quantity
   */
  updateQuantity(productId: number, quantity: number): void {
    const currentCart = this.cartSubject.value;
    const item = currentCart.items.find(item => item.product.id === productId);
    
    if (item) {
      if (quantity <= 0) {
        this.removeFromCart(productId);
      } else {
        item.quantity = quantity;
        this.updateCart(currentCart);
      }
    }
  }

  /**
   * Get current cart
   */
  getCart(): Cart {
    return this.cartSubject.value;
  }

  /**
   * Clear entire cart
   */
  clearCart(): void {
    this.updateCart({ items: [], subtotal: 0, itemCount: 0 });
  }

  /**
   * Get cart item count
   */
  getItemCount(): number {
    return this.cartSubject.value.itemCount;
  }

  /**
   * Get cart subtotal
   */
  getSubtotal(): number {
    return this.cartSubject.value.subtotal;
  }

  /**
   * Check if product is in cart
   */
  isInCart(productId: number): boolean {
    return this.cartSubject.value.items.some(item => item.product.id === productId);
  }

  /**
   * Get quantity of product in cart
   */
  getProductQuantity(productId: number): number {
    const item = this.cartSubject.value.items.find(item => item.product.id === productId);
    return item ? item.quantity : 0;
  }

  // Private helper methods

  private updateCart(cart: Cart): void {
    cart.itemCount = cart.items.reduce((sum, item) => sum + item.quantity, 0);
    cart.subtotal = cart.items.reduce((sum, item) => 
      sum + (item.product.price * item.quantity), 0);
    
    this.cartSubject.next(cart);
    this.saveCartToStorage(cart);
  }

  private loadCartFromStorage(): Cart {
    const savedCart = localStorage.getItem(this.STORAGE_KEY);
    if (savedCart) {
      try {
        return JSON.parse(savedCart);
      } catch (e) {
        console.error('Error loading cart from storage', e);
      }
    }
    return { items: [], subtotal: 0, itemCount: 0 };
  }

  private saveCartToStorage(cart: Cart): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(cart));
  }
}
