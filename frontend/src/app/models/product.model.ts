export interface Product {
  id: number;
  name: string;
  description: string;
  category: string;
  price: number;
  mrp: number;
  stockQuantity: number;
  imageUrl: string;
  sku: string;
  weight?: string;
  dimensions?: string;
  createdAt: Date;
}

export interface ProductPage {
  content: Product[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
