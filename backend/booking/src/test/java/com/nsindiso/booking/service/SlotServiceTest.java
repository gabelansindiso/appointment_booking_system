package com.nsindiso.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsindiso.booking.config.BookingProperties;
import com.nsindiso.booking.dto.Availability;
import com.nsindiso.booking.dto.Slot;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.Branch;
import com.nsindiso.booking.entity.TestBranches;
import com.nsindiso.booking.exception.BadRequestException;
import com.nsindiso.booking.exception.NotFoundException;
import com.nsindiso.booking.exception.SlotUnavailableException;
import com.nsindiso.booking.repository.AppointmentRepository;
import com.nsindiso.booking.repository.BranchRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    // Wednesday 2026-10-28 08:00 SAST
    private static final Instant NOW = Instant.parse("2026-10-28T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock BranchRepository branches;
    @Mock AppointmentRepository appointments;

    private SlotService service;

    @BeforeEach
    void setUp () {
        BookingProperties props = new BookingProperties(Duration.ofHours(1), 60, Duration.ofHours(2), 2);
        service = new SlotService(branches, appointments, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Branch givenBranch () {
        Branch branch = TestBranches.weekdayBranch(BRANCH_ID, 30);
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        return branch;
    }

    private void givenBooked (Instant... starts) {
        when(appointments.findStartsBetween(eq(BRANCH_ID), eq(Appointment.Status.CONFIRMED), any(), any()))
                .thenReturn(Set.of(starts));
    }

    @Test
    void availabilityForFullWeekdayHasSeventeenHalfHourSlots () {
        givenBranch();
        givenBooked();

        Availability availability = service.availability(BRANCH_ID, TODAY.plusDays(1));

        // 08:30-17:00 = 8.5h = 17 slots
        assertThat(availability.slots()).hasSize(17);
        assertThat(availability.slots().getFirst().start()).isEqualTo(Instant.parse("2026-10-29T06:30:00Z"));
    }

    @Test
    void availabilityTodayRespectsMinimumLeadTime () {
        givenBranch();
        givenBooked();

        Availability availability = service.availability(BRANCH_ID, TODAY);

        // now 08:00 local + 1h lead => first slot at 09:00 local
        assertThat(availability.slots().getFirst().start()).isEqualTo(Instant.parse("2026-10-28T07:00:00Z"));
    }

    @Test
    void availabilityQueriesBookedSlotsForTheLocalDay () {
        givenBranch();
        givenBooked(Instant.parse("2026-10-29T06:30:00Z"));

        Availability availability = service.availability(BRANCH_ID, TODAY.plusDays(1));

        assertThat(availability.slots()).extracting(Slot::start)
                .doesNotContain(Instant.parse("2026-10-29T06:30:00Z"));
        // local midnight to midnight, not UTC
        verify(appointments).findStartsBetween(BRANCH_ID, Appointment.Status.CONFIRMED,
                Instant.parse("2026-10-28T22:00:00Z"), Instant.parse("2026-10-29T22:00:00Z"));
    }

    @Test
    void availabilityOnSundayIsEmpty () {
        givenBranch();
        givenBooked();

        assertThat(service.availability(BRANCH_ID, LocalDate.of(2026, 11, 1)).slots()).isEmpty();
    }

    @Test
    void rejectsPastDates () {
        givenBranch();

        assertThatThrownBy(() -> service.availability(BRANCH_ID, TODAY.minusDays(1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsDatesBeyondMaxAdvanceDays () {
        givenBranch();

        assertThatThrownBy(() -> service.availability(BRANCH_ID, TODAY.plusDays(61)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsLastDayOfBookingWindow () {
        givenBranch();
        givenBooked();

        service.availability(BRANCH_ID, TODAY.plusDays(60));
    }

    @Test
    void unknownBranchIsNotFound () {
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.availability(BRANCH_ID, TODAY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireValidSlotReturnsMatchingSlotWithEnd () {
        givenBranch();
        givenBooked();
        Instant start = Instant.parse("2026-10-29T08:00:00Z");

        Slot slot = service.requireValidSlot(BRANCH_ID, start);

        assertThat(slot).isEqualTo(new Slot(start, start.plus(Duration.ofMinutes(30))));
    }

    @Test
    void requireValidSlotRejectsMisalignedStart () {
        givenBranch();
        givenBooked();

        assertThatThrownBy(() -> service.requireValidSlot(BRANCH_ID, Instant.parse("2026-10-29T08:10:00Z")))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void requireValidSlotRejectsAlreadyBookedSlot () {
        givenBranch();
        Instant start = Instant.parse("2026-10-29T08:00:00Z");
        givenBooked(start);

        assertThatThrownBy(() -> service.requireValidSlot(BRANCH_ID, start))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void requireValidSlotRejectsOutsideOpeningHours () {
        givenBranch();
        givenBooked();

        // 17:00 local: closing time, no slot starts there
        assertThatThrownBy(() -> service.requireValidSlot(BRANCH_ID, Instant.parse("2026-10-29T15:00:00Z")))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void requireValidSlotUsesBranchLocalDateNotUtcDate () {
        givenBranch();
        givenBooked();
        // 2026-10-29T22:30Z is 00:30 on Friday 30th local: closed, and must not resolve against Thursday's hours
        assertThatThrownBy(() -> service.requireValidSlot(BRANCH_ID, Instant.parse("2026-10-29T22:30:00Z")))
                .isInstanceOf(SlotUnavailableException.class);
        verify(appointments).findStartsBetween(BRANCH_ID, Appointment.Status.CONFIRMED,
                Instant.parse("2026-10-29T22:00:00Z"), Instant.parse("2026-10-30T22:00:00Z"));
    }
}
