import { Component, effect, inject, input, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiProblem, Appointment, AppointmentStatus, Slot } from '../../core/api.models';
import { BookingApi } from '../../core/booking-api';
import { errorMessage } from '../../core/form-helpers';
import { SlotPicker } from '../../shared/slot-picker';
import { ZonedPipe } from '../../shared/zoned.pipe';

type Mode = 'view' | 'reschedule' | 'confirm-cancel';

const STATUS_LABELS: Record<AppointmentStatus, string> = {
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
  NO_SHOW: 'No show',
};

@Component({
  selector: 'app-manage-page',
  imports: [ReactiveFormsModule, RouterLink, SlotPicker, ZonedPipe],
  templateUrl: './manage.page.html',
})
export class ManagePage {
  private readonly api = inject(BookingApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly picker = viewChild(SlotPicker);

  readonly reference = input<string>();

  protected readonly errorMessage = errorMessage;

  protected readonly lookupForm = this.fb.group({
    reference: ['', [Validators.required, Validators.maxLength(20)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
  });

  protected readonly appointment = signal<Appointment | null>(null);
  protected readonly mode = signal<Mode>('view');
  protected newSlot = signal<Slot | null>(null);
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  private email = '';

  constructor() {
    effect(() => {
      const ref = this.reference();
      if (ref) this.lookupForm.controls.reference.setValue(ref);
    });
  }

  protected statusLabel(status: AppointmentStatus): string {
    return STATUS_LABELS[status];
  }

  protected lookup(): void {
    this.submitted.set(true);
    this.lookupForm.markAllAsTouched();
    if (this.lookupForm.invalid || this.busy()) return;
    const { reference, email } = this.lookupForm.getRawValue();
    this.run(this.api.lookup(reference.trim(), email.trim()), (appt) => {
      this.email = email.trim();
      this.appointment.set(appt);
      this.mode.set('view');
    });
  }

  protected startReschedule(): void {
    this.error.set(null);
    this.notice.set(null);
    this.newSlot.set(null);
    this.mode.set('reschedule');
  }

  protected confirmReschedule(): void {
    const appt = this.appointment();
    const slot = this.newSlot();
    if (!appt || !slot || this.busy()) return;
    this.run(this.api.reschedule(appt.reference, this.email, slot.start), (updated) => {
      this.appointment.set(updated);
      this.newSlot.set(null);
      this.mode.set('view');
      this.notice.set('Your appointment has moved. A new confirmation has been sent.');
    });
  }

  protected confirmCancel(): void {
    const appt = this.appointment();
    if (!appt || this.busy()) return;
    this.run(this.api.cancel(appt.reference, this.email), (updated) => {
      this.appointment.set(updated);
      this.mode.set('view');
      this.notice.set('Your appointment is cancelled.');
    });
  }

  protected backToView(): void {
    this.error.set(null);
    this.mode.set('view');
  }

  protected reset(): void {
    this.appointment.set(null);
    this.error.set(null);
    this.notice.set(null);
    this.email = '';
    this.submitted.set(false);
    this.lookupForm.reset();
  }

  private run(call$: Observable<Appointment>, onSuccess: (a: Appointment) => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    call$.subscribe({
      next: (a) => {
        this.busy.set(false);
        onSuccess(a);
      },
      error: (p: ApiProblem) => {
        this.busy.set(false);
        this.error.set(p.detail);
        if (p.status === 409) this.picker()?.refresh();
      },
    });
  }
}
