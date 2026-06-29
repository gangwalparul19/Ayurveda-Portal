import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { Product } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class WishlistService {
  private readonly STORAGE_KEY = 'shifa_wishlist';
  private wishlistSubject: BehaviorSubject<Product[]>;
  public wishlist$: Observable<Product[]>;

  constructor() {
    const savedWishlist = this.loadWishlistFromStorage();
    this.wishlistSubject = new BehaviorSubject<Product[]>(savedWishlist);
    this.wishlist$ = this.wishlistSubject.asObservable();
  }

  /**
   * Add product to wishlist
   */
  addToWishlist(product: Product): void {
    const currentWishlist = this.wishlistSubject.value;
    if (!this.isInWishlist(product.id)) {
      currentWishlist.push(product);
      this.updateWishlist(currentWishlist);
    }
  }

  /**
   * Remove product from wishlist
   */
  removeFromWishlist(productId: number): void {
    const currentWishlist = this.wishlistSubject.value;
    const updatedWishlist = currentWishlist.filter(p => p.id !== productId);
    this.updateWishlist(updatedWishlist);
  }

  /**
   * Check if product is in wishlist
   */
  isInWishlist(productId: number): boolean {
    return this.wishlistSubject.value.some(p => p.id === productId);
  }

  /**
   * Get wishlist
   */
  getWishlist(): Product[] {
    return this.wishlistSubject.value;
  }

  /**
   * Toggle product in wishlist
   */
  toggleWishlist(product: Product): void {
    if (this.isInWishlist(product.id)) {
      this.removeFromWishlist(product.id);
    } else {
      this.addToWishlist(product);
    }
  }

  /**
   * Clear wishlist
   */
  clearWishlist(): void {
    this.updateWishlist([]);
  }

  /**
   * Get wishlist count
   */
  getCount(): number {
    return this.wishlistSubject.value.length;
  }

  // Private helper methods

  private updateWishlist(wishlist: Product[]): void {
    this.wishlistSubject.next(wishlist);
    this.saveWishlistToStorage(wishlist);
  }

  private loadWishlistFromStorage(): Product[] {
    const saved = localStorage.getItem(this.STORAGE_KEY);
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        console.error('Error loading wishlist from storage', e);
      }
    }
    return [];
  }

  private saveWishlistToStorage(wishlist: Product[]): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(wishlist));
  }
}
