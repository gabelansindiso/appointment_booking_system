export interface Branch {
  id: string;
  name: string;
  address: string;
  timezone: string;
}

export interface ServiceType {
  code: string;
  name: string;
}

export interface Slot {
  start: string;
  end: string;
}

export interface Availability {
  branchId: string;
  date: string;
  timezone: string;
  slotMinutes: number;
  slots: Slot[];
}

export type AppointmentStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export interface Appointment {
  reference: string;
  branchId: string;
  branchName: string;
  serviceTypeCode: string;
  serviceTypeName: string;
  start: string;
  end: string;
  timezone: string;
  status: AppointmentStatus;
  customerName: string;
  reschedulesRemaining: number;
}

export interface CreateAppointmentRequest {
  branchId: string;
  serviceType: string;
  start: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
}

export interface ApiProblem {
  status: number;                    // 0 = network failure
  detail: string;
  errors?: Record<string, string>;   // field-level validation errors (400)
}
