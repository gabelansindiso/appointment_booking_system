import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BookingStore } from '../../core/booking-store';
import { maskEmail } from '../../core/time';
import { ZonedPipe } from '../../shared/zoned.pipe';

@Component({
  selector: 'app-confirmation-page',
  imports: [RouterLink, ZonedPipe],
  templateUrl: './confirmation.page.html',
})
export class ConfirmationPage {
  protected readonly booking = inject(BookingStore).last;
  protected readonly maskedEmail = computed(() => {
    const b = this.booking();
    return b ? maskEmail(b.email) : '';
  });
}
