import { Injectable, signal } from '@angular/core';
import { Appointment } from './api.models';

@Injectable({ providedIn: 'root' })
export class BookingStore {
  readonly last = signal<{ appointment: Appointment; email: string } | null>(null);
}
