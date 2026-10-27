import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Appointment, Availability, Branch, CreateAppointmentRequest, ServiceType } from './api.models';

@Injectable({ providedIn: 'root' })
export class BookingApi {
  private readonly http = inject(HttpClient);

  branches(): Observable<Branch[]> {
    return this.http.get<Branch[]>('/api/branches');
  }

  serviceTypes(): Observable<ServiceType[]> {
    return this.http.get<ServiceType[]>('/api/service-types');
  }

  availability(branchId: string, date: string): Observable<Availability> {
    return this.http.get<Availability>(`/api/branches/${branchId}/availability`, { params: { date } });
  }

  create(req: CreateAppointmentRequest, idempotencyKey: string): Observable<Appointment> {
    return this.http.post<Appointment>('/api/appointments', req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  lookup(reference: string, email: string): Observable<Appointment> {
    return this.http.post<Appointment>('/api/appointments/lookup', { reference, email });
  }

  cancel(reference: string, email: string): Observable<Appointment> {
    return this.http.post<Appointment>(`/api/appointments/${encodeURIComponent(reference)}/cancel`, { email });
  }

  reschedule(reference: string, email: string, newStart: string): Observable<Appointment> {
    return this.http.post<Appointment>(
      `/api/appointments/${encodeURIComponent(reference)}/reschedule`, { email, newStart });
  }
}
