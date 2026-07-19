/** Service assignments (PatientProgramme) — mirrors PatientProgrammeDto. */

export type ServiceType = 'programme' | 'session' | 'challenge';
export type AssignmentStatus = 'active' | 'completed' | 'cancelled';
export type AssignmentOrderStatus = 'active' | 'completed' | 'deactivated';
export type OrderPaymentStatus = 'paid' | 'unpaid' | 'pending';
export type TransactionType = 'activation' | 'deactivation' | 'refund';
export type PaymentType = 'offline' | 'online' | 'credit';

export interface AssignmentTransaction {
  id: string;
  transactionId: string;
  invoiceNumber: string;
  transactionType: TransactionType;
  paymentType: PaymentType;
  amountInr: number;
  createdAt: string;
}

export interface AssignmentOrder {
  id: string;
  amountInr: number;
  status: AssignmentOrderStatus;
  paymentStatus: OrderPaymentStatus;
  transactions: AssignmentTransaction[];
}

export interface PatientProgramme {
  id: string;
  serviceType: ServiceType;
  /** Absent when the (archived) catalogue item was deleted after assignment. */
  catalogueItem?: { id: string; name: string; serviceCode: string };
  startDate: string;
  /** Sessions are single-day appointments: endDate equals startDate. */
  endDate?: string;
  priceInr: number;
  status: AssignmentStatus;
  notes?: string;
  assignedBy?: { id: string; name: string };
  assignedDoctor?: { id: string; name: string };
  order?: AssignmentOrder;
  createdAt: string;
  updatedAt: string;
}

/** True once money has been recorded — deletion is blocked, refunds only. */
export function hasNonRefundTransaction(assignment: PatientProgramme): boolean {
  return (assignment.order?.transactions ?? []).some((t) => t.transactionType !== 'refund');
}
