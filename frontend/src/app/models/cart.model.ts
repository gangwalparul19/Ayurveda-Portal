import { Product } from './product.model';

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface Cart {
  items: CartItem[];
  subtotal: number;
  itemCount: number;
}

export interface OrderRequest {
  customerName: string;
  customerPhone: string;
  customerEmail?: string;
  deliveryAddress: string;
  city: string;
  state: string;
  pincode: string;
  notes?: string;
  paymentMethod: string;
  items: OrderItemRequest[];
  couponCode?: string;
  couponDiscount?: number;
}

export interface OrderItemRequest {
  productId: number;
  productName: string;
  quantity: number;
  price: number;
}
