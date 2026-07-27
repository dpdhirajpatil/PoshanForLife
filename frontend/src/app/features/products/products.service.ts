import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import { Product, ProductSegment, ProductStatus, SegmentStatus } from '../../core/models/product.model';
import { ApiService } from '../../core/services/api.service';

export interface ProductListQuery {
  segmentId?: string;
  status?: ProductStatus;
  search?: string;
  page: number;
  limit: number;
}

/** Not pre-grouped by segment — the page component groups the flat list client-side. */
export interface ProductListResult {
  products: Product[];
}

export interface SaveSegmentPayload {
  name?: string;
  displayOrder?: number;
  status?: SegmentStatus;
}

export interface SaveProductPayload {
  segmentId?: string;
  name?: string;
  description?: string;
  priceInr?: number | null;
  sku?: string;
  status?: ProductStatus;
  displayOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class ProductsService {
  private readonly api = inject(ApiService);

  // ---- segments -----------------------------------------------------------

  listSegments(includeArchived = false): Observable<ProductSegment[]> {
    return this.api.get<ProductSegment[]>('/products/segments', { includeArchived });
  }

  createSegment(payload: { name: string; displayOrder?: number }): Observable<ProductSegment> {
    return this.api.post<ProductSegment>('/products/segments', payload);
  }

  updateSegment(id: string, payload: SaveSegmentPayload): Observable<ProductSegment> {
    return this.api.patch<ProductSegment>(`/products/segments/${id}`, payload);
  }

  removeSegment(id: string): Observable<unknown> {
    return this.api.delete(`/products/segments/${id}`);
  }

  // ---- products -------------------------------------------------------------

  list(query: ProductListQuery): Observable<Paged<ProductListResult>> {
    return this.api.getPaged<ProductListResult>('/products', { ...query });
  }

  get(id: string): Observable<Product> {
    return this.api.get<Product>(`/products/${id}`);
  }

  create(payload: SaveProductPayload & { segmentId: string; name: string }): Observable<Product> {
    return this.api.post<Product>('/products', payload);
  }

  update(id: string, payload: SaveProductPayload): Observable<Product> {
    return this.api.patch<Product>(`/products/${id}`, payload);
  }

  remove(id: string): Observable<unknown> {
    return this.api.delete(`/products/${id}`);
  }

  /** Appends the uploaded image and resolves to the product's full updated state. */
  uploadImage(id: string, file: File): Observable<Product> {
    const form = new FormData();
    form.append('file', file);
    return this.api.post<Product>(`/products/${id}/upload-image`, form);
  }

  removeImage(id: string, url: string): Observable<Product> {
    return this.api.delete<Product>(`/products/${id}/images?url=${encodeURIComponent(url)}`);
  }
}
