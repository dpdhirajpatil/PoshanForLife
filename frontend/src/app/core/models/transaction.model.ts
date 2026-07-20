import {
  AssignmentStatus,
  PaymentType,
  ServiceType,
  TransactionType,
} from './patient-programme.model';

/** Transactions & invoices — mirrors TransactionListItemDto / TransactionDetailDto. */

export interface TransactionListItem {
  id: string;
  transactionId: string;
  invoiceNumber: string;
  transactionType: TransactionType;
  paymentType: PaymentType;
  patient: { id: string; name: string };
  /** Absent for orders whose assignment no longer exists. */
  catalogueType?: ServiceType;
  serviceName?: string;
  amountInr: number;
  /** Zero-or-negative; negative = credit consumed. */
  creditCharged: number;
  createdBy: { id: string; name: string };
  createdAt: string;
}

/** Computed over the CURRENT FILTER SET, not just the current page. */
export interface TransactionTotals {
  totalTransactionValue: number;
  /** Non-negative — the absolute value of consumed credit. */
  totalCreditConsumed: number;
}

export interface TransactionListResponse {
  transactions: TransactionListItem[];
  summary: TransactionTotals;
}

export interface TransactionOrderProgramme {
  id: string;
  serviceType: ServiceType;
  catalogueItem?: {
    id: string;
    name: string;
    serviceCode: string;
    durationWeeks?: number;
    durationMinutes?: number;
    durationDays?: number;
  };
  startDate: string;
  endDate?: string;
  status: AssignmentStatus;
  assignedBy?: { id: string; name: string };
  assignedDoctor?: { id: string; name: string };
}

export interface TransactionDetail {
  id: string;
  transactionId: string;
  invoiceNumber: string;
  transactionType: TransactionType;
  paymentType: PaymentType;
  priceInr: number;
  discountInr: number;
  amountInr: number;
  creditCharged: number;
  source: string;
  paymentGatewayRef?: string;
  notes?: string;
  createdAt: string;
  patient: { id: string; name: string; email: string; phone?: string };
  createdBy?: { id: string; name: string };
  order?: {
    id: string;
    patientProgramme?: TransactionOrderProgramme;
  };
}
