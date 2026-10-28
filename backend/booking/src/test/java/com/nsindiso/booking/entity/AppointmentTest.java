package com.nsindiso.booking.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsindiso.booking.exception.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppointmentTest {

    private static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");
    private static final Instant START = Instant.parse("2026-11-02T10:00:00Z");
    private static final Duration CUTOFF = Duration.ofHours(2);
    private static final int MAX_RESCHEDULES = 2;

    private static Appointment confirmed () {
        return Appointment.confirm(UUID.randomUUID(), "CAP-ABC234", UUID.randomUUID(), UUID.randomUUID(),
                START, START.plus(Duration.ofMinutes(30)), "Jane Doe", "jane@example.com",
                "+27821234567", "key-1", NOW);
    }

    @Test
    void confirmCreatesConfirmedAppointmentWithNoReschedules () {
        Appointment a = confirmed();

        assertThat(a.getStatus()).isEqualTo(Appointment.Status.CONFIRMED);
        assertThat(a.getRescheduleCount()).isZero();
        assertThat(a.getVersion()).as("null version marks the entity as new").isNull();
    }

    @Nested
    class MatchesEmail {

        @ParameterizedTest
        @ValueSource(strings = {"jane@example.com", "JANE@Example.COM", "  jane@example.com  "})
        void ignoresCaseAndSurroundingWhitespace (String candidate) {
            assertThat(confirmed().matchesEmail(candidate)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"john@example.com", "jane@example.co", "", "jane @example.com"})
        void rejectsDifferentEmails (String candidate) {
            assertThat(confirmed().matchesEmail(candidate)).isFalse();
        }

        @Test
        void rejectsNull () {
            assertThat(confirmed().matchesEmail(null)).isFalse();
        }
    }

    @Nested
    class Cancel {

        @Test
        void cancelsConfirmedFutureAppointment () {
            Appointment a = confirmed();

            assertThat(a.cancel(NOW)).isTrue();
            assertThat(a.getStatus()).isEqualTo(Appointment.Status.CANCELLED);
        }

        @Test
        void isIdempotentWhenAlreadyCancelled () {
            Appointment a = confirmed();
            a.cancel(NOW);

            assertThat(a.cancel(NOW)).isFalse();
            assertThat(a.getStatus()).isEqualTo(Appointment.Status.CANCELLED);
        }

        @Test
        void alreadyCancelledIsIdempotentEvenAfterStartTime () {
            Appointment a = confirmed();
            a.cancel(NOW);

            assertThat(a.cancel(START.plusSeconds(60))).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = Appointment.Status.class, names = {"COMPLETED", "NO_SHOW"})
        void rejectsNonConfirmedStatuses (Appointment.Status status) {
            Appointment a = confirmed();
            a.setStatus(status);

            assertThatThrownBy(() -> a.cancel(NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only confirmed");
            assertThat(a.getStatus()).isEqualTo(status);
        }

        @Test
        void rejectsCancellationAtExactStartTime () {
            Appointment a = confirmed();

            assertThatThrownBy(() -> a.cancel(START))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already started");
            assertThat(a.getStatus()).isEqualTo(Appointment.Status.CONFIRMED);
        }

        @Test
        void allowsCancellationOneSecondBeforeStart () {
            assertThat(confirmed().cancel(START.minusSeconds(1))).isTrue();
        }
    }

    @Nested
    class EnsureReschedulable {

        private final Instant newStart = START.plus(Duration.ofDays(1));

        @Test
        void passesForConfirmedAppointmentOutsideCutoff () {
            confirmed().ensureReschedulable(newStart, NOW, CUTOFF, MAX_RESCHEDULES);
        }

        @Test
        void rejectsCancelledAppointment () {
            Appointment a = confirmed();
            a.cancel(NOW);

            assertThatThrownBy(() -> a.ensureReschedulable(newStart, NOW, CUTOFF, MAX_RESCHEDULES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only confirmed");
        }

        @Test
        void rejectsWhenRescheduleLimitReached () {
            Appointment a = confirmed();
            a.setRescheduleCount(MAX_RESCHEDULES);

            assertThatThrownBy(() -> a.ensureReschedulable(newStart, NOW, CUTOFF, MAX_RESCHEDULES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maximum number");
        }

        @Test
        void allowsLastRemainingReschedule () {
            Appointment a = confirmed();
            a.setRescheduleCount(MAX_RESCHEDULES - 1);

            a.ensureReschedulable(newStart, NOW, CUTOFF, MAX_RESCHEDULES);
        }

        @Test
        void rejectsInsideCutoffWindow () {
            Instant tooLate = START.minus(CUTOFF).plusSeconds(1);

            assertThatThrownBy(() -> confirmed().ensureReschedulable(newStart, tooLate, CUTOFF, MAX_RESCHEDULES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("this close");
        }

        @Test
        void allowsExactlyAtCutoffBoundary () {
            confirmed().ensureReschedulable(newStart, START.minus(CUTOFF), CUTOFF, MAX_RESCHEDULES);
        }

        @Test
        void rejectsSameStartTime () {
            assertThatThrownBy(() -> confirmed().ensureReschedulable(START, NOW, CUTOFF, MAX_RESCHEDULES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("same as the current");
        }
    }

    @Test
    void moveToUpdatesTimesAndIncrementsRescheduleCount () {
        Appointment a = confirmed();
        Instant newStart = START.plus(Duration.ofDays(1));
        Instant newEnd = newStart.plus(Duration.ofMinutes(30));

        a.moveTo(newStart, newEnd);
        a.moveTo(newStart.plusSeconds(1800), newEnd.plusSeconds(1800));

        assertThat(a.getStartAt()).isEqualTo(newStart.plusSeconds(1800));
        assertThat(a.getEndAt()).isEqualTo(newEnd.plusSeconds(1800));
        assertThat(a.getRescheduleCount()).isEqualTo(2);
    }
}
