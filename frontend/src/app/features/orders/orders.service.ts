import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import { OrderDetail, OrderListItem, OrderStatus } from '../../core/models/order.model';
import { OrderPaymentStatus } from '../../core/models/patient-programme.model';
import { ApiService } from '../../core/services/api.service';

export interface OrderListQuery {
  status?: OrderStatus;
  paymentStatus?: OrderPaymentStatus;
  search?: string;
  /** ISO dates (yyyy-MM-dd), inclusive. */
  dateFrom?: string;
  dateTo?: string;
  page: number;
  limit: number;
}

export interface UpdateOrderPayload {
  paymentStatus?: OrderPaymentStatus;
  status?: OrderStatus;
  notes?: string;
}

/**
 * Orders are created by the assign-a-service flow, never directly — the
 * backend has no POST /orders. DOCTOR callers are auto-scoped server-side.
 */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly api = inject(ApiService);

  list(query: OrderListQuery): Observable<Paged<OrderListItem[]>> {
    return this.api.getPaged<OrderListItem[]>('/orders', { ...query });
  }

  get(id: string): Observable<OrderDetail> {
    return this.api.get<OrderDetail>(`/orders/${id}`);
  }

  /** Marking paid generates an activation transaction + invoice server-side. */
  update(id: string, payload: UpdateOrderPayload): Observable<OrderDetail> {
    return this.api.patch<OrderDetail>(`/orders/${id}`, payload);
  }
}
