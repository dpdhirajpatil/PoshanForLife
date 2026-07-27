/** Products (physical goods) — distinct from Catalogue's services. Mirrors ProductDto/ProductSegmentDto. */

export type ProductStatus = 'draft' | 'published';
export const PRODUCT_STATUSES: ProductStatus[] = ['draft', 'published'];

export type SegmentStatus = 'active' | 'archived';
export const SEGMENT_STATUSES: SegmentStatus[] = ['active', 'archived'];

export interface ProductSegment {
  id: string;
  name: string;
  displayOrder: number;
  status: SegmentStatus;
  publishedProductCount: number;
  createdAt: string;
}

export interface Product {
  id: string;
  segmentId: string;
  segmentName: string;
  name: string;
  description?: string;
  images: string[];
  priceInr?: number;
  sku?: string;
  status: ProductStatus;
  displayOrder: number;
  createdBy?: { id: string; name: string };
  createdAt: string;
}
