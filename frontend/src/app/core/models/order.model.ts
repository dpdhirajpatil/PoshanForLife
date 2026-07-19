import {
  AssignmentStatus,
  AssignmentTransaction,
  OrderPaymentStatus,
  ServiceType,
} from './patient-programme.model';

/** Orders — mirrors OrderListItemDto / OrderDetailDto. */

export type OrderStatus = 'active' | 'completed' | 'deactivated';

export interface OrderListItem {
  id: string;
  patient: { id: string; name: string };
  /** Absent for orphaned orders (assignment deleted). */
  serviceType?: ServiceType;
  serviceName?: string;
  amountInr: number;
  status: OrderStatus;
  paymentStatus: OrderPaymentStatus;
  createdAt: string;
}

export interface OrderDetail {
  id: string;
  amountInr: number;
  status: OrderStatus;
  paymentStatus: OrderPaymentStatus;
  notes?: string;
  patient: { id: string; name: string; email: string; phone?: string };
  patientProgramme?: {
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
  };
  transactions: AssignmentTransaction[];
  createdBy?: { id: string; name: string };
  createdAt: string;
  updatedAt: string;
}
