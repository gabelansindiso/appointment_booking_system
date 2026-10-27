import {
  Component,
  computed,
  effect,
  inject,
  input,
  model,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, map, of, switchMap, tap } from 'rxjs';
import { ApiProblem, Slot } from '../core/api.models';
import { BookingApi } from '../core/booking-api';
import { addDays, todayIn } from '../core/time';
import { ZonedPipe } from './zoned.pipe';

const MAX_ADVANCE_DAYS = 60;

@Component({
  selector: 'app-slot-picker',
  imports: [ZonedPipe],
  templateUrl: './slot-picker.html',
})
export class SlotPicker {
  private readonly api = inject(BookingApi);

  readonly branchId = input.required<string>();
  readonly timezone = input.required<string>();

  readonly excludeStart = input<string | null>(null);
  readonly selected = model<Slot | null>(null);

  protected readonly date = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly slots = signal<Slot[]>([]);

  protected readonly minDate = computed(() => todayIn(this.timezone()));
  protected readonly maxDate = computed(() => addDays(this.minDate(), MAX_ADVANCE_DAYS));
  protected readonly visibleSlots = computed(() =>
    this.slots().filter((s) => s.start !== this.excludeStart()),
  );

  private readonly requests = new Subject<{ branchId: string; date: string }>();

  constructor() {
    // switchMap drops stale responses when the date changes quickly.
    this.requests
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((r) =>
          this.api.availability(r.branchId, r.date).pipe(
            map((a) => a.slots),
            catchError((p: ApiProblem) => {
              this.error.set(p.detail);
              return of([] as Slot[]);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((slots) => {
        this.slots.set(slots);
        this.loading.set(false);
      });

    // Reload when the branch or date changes; the old selection is no longer valid.
    effect(() => {
      const branchId = this.branchId();
      const date = this.date();
      untracked(() => {
        this.selected.set(null);
        if (date) this.requests.next({ branchId, date });
      });
    });
  }

  refresh(): void {
    this.selected.set(null);
    const date = this.date();
    if (date) this.requests.next({ branchId: this.branchId(), date });
  }

  protected onDateChange(event: Event): void {
    this.date.set((event.target as HTMLInputElement).value);
  }
}
