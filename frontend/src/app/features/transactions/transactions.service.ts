import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import {
  PaymentType,
  ServiceType,
  TransactionType,
} from '../../core/models/patient-programme.model';
import {
  TransactionDetail,
  TransactionListResponse,
} from '../../core/models/transaction.model';
import { ApiService } from '../../core/services/api.service';

export interface TransactionListQuery {
  search?: string;
  /** Practitioner (createdBy) — ADMIN only; ignored server-side for DOCTOR callers. */
  userId?: string;
  catalogue?: ServiceType;
  paymentType?: PaymentType;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  limit: number;
}

export interface CreateTransactionPayload {
  orderId: string;
  transactionType: TransactionType;
  amountInr: number;
  discountInr?: number;
  paymentType?: PaymentType;
  paymentGatewayRef?: string;
  notes?: string;
}

/**
 * The financial ledger. Reads are ADMIN+DOCTOR (DOCTOR scoped server-side);
 * manual entry is ADMIN-only. Most transactions are created as a side effect
 * of assigning a service or marking an order paid — this create() is for
 * offline manual entries (or a future payment-gateway webhook).
 */
@Injectable({ providedIn: 'root' })
export class TransactionsService {
  private readonly api = inject(ApiService);

  list(query: TransactionListQuery): Observable<Paged<TransactionListResponse>> {
    return this.api.getPaged<TransactionListResponse>('/transactions', { ...query });
  }

  get(id: string): Observable<TransactionDetail> {
    return this.api.get<TransactionDetail>(`/transactions/${id}`);
  }

  create(payload: CreateTransactionPayload): Observable<TransactionDetail> {
    return this.api.post<TransactionDetail>('/transactions', payload);
  }
}
