import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  ApiProblem,
  Branch,
  CreateAppointmentRequest,
  ServiceType,
  Slot,
} from '../../core/api.models';
import { BookingApi } from '../../core/booking-api';
import { BookingStore } from '../../core/booking-store';
import { errorMessage, normalisePhone, phoneValidator } from '../../core/form-helpers';
import { IdempotencyKeys } from '../../core/idempotency-keys';
import { SlotPicker } from '../../shared/slot-picker';
import { ZonedPipe } from '../../shared/zoned.pipe';

@Component({
  selector: 'app-book-page',
  imports: [ReactiveFormsModule, SlotPicker, ZonedPipe],
  templateUrl: './book.page.html',
})
export class BookPage {
  private readonly api = inject(BookingApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly store = inject(BookingStore);
  private readonly keys = new IdempotencyKeys();
  private readonly picker = viewChild(SlotPicker);

  protected readonly errorMessage = errorMessage;

  protected readonly branches = signal<Branch[]>([]);
  protected readonly serviceTypes = signal<ServiceType[]>([]);
  protected readonly loadingCatalog = signal(true);
  protected readonly catalogError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    serviceType: ['', Validators.required],
    branchId: ['', Validators.required],
    customerName: ['', [Validators.required, Validators.maxLength(100)]],
    customerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    customerPhone: ['', [Validators.required, phoneValidator]],
  });

  private readonly branchId = toSignal(this.form.controls.branchId.valueChanges, {
    initialValue: '',
  });
  protected readonly branch = computed(() => this.branches().find((b) => b.id === this.branchId()));

  protected slot = signal<Slot | null>(null);
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  constructor() {
    this.loadCatalog();
  }

  protected loadCatalog(): void {
    this.loadingCatalog.set(true);
    this.catalogError.set(null);
    forkJoin({ branches: this.api.branches(), serviceTypes: this.api.serviceTypes() }).subscribe({
      next: ({ branches, serviceTypes }) => {
        this.branches.set(branches);
        this.serviceTypes.set(serviceTypes);
        this.loadingCatalog.set(false);
      },
      error: (p: ApiProblem) => {
        this.catalogError.set(p.detail);
        this.loadingCatalog.set(false);
      },
    });
  }

  protected submit(): void {
    this.submitted.set(true);
    this.submitError.set(null);
    this.form.markAllAsTouched();
    const slot = this.slot();
    if (this.form.invalid || !slot || this.submitting()) return;

    const v = this.form.getRawValue();
    const request: CreateAppointmentRequest = {
      branchId: v.branchId,
      serviceType: v.serviceType,
      start: slot.start,
      customerName: v.customerName.trim(),
      customerEmail: v.customerEmail.trim(),
      customerPhone: normalisePhone(v.customerPhone),
    };

    this.submitting.set(true);
    this.api.create(request, this.keys.keyFor(request)).subscribe({
      next: (appointment) => {
        this.keys.reset();
        this.store.last.set({ appointment, email: request.customerEmail });
        void this.router.navigate(['/confirmation']);
      },
      error: (p: ApiProblem) => {
        this.submitting.set(false);
        this.handleError(p);
      },
    });
  }

  private handleError(p: ApiProblem): void {
    if (p.status === 409) {
      this.slot.set(null);
      this.picker()?.refresh();
      this.submitError.set('That time was just taken. Pick another.');
      return;
    }
    if (p.status === 400 && p.errors) {
      const controls = this.form.controls as Record<string, AbstractControl>;
      for (const [field, msg] of Object.entries(p.errors)) {
        const control = controls[field];
        if (control) {
          control.setErrors({ server: msg });
          control.markAsTouched();
        }
      }
      this.submitError.set('Fix the highlighted fields and try again.');
      return;
    }
    this.submitError.set(p.detail);
  }
}
