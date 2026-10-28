package com.nsindiso.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nsindiso.booking.config.BookingProperties;
import com.nsindiso.booking.dto.AppointmentCancelled;
import com.nsindiso.booking.dto.AppointmentRescheduled;
import com.nsindiso.booking.dto.Slot;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.AppointmentHistory;
import com.nsindiso.booking.exception.BusinessRuleException;
import com.nsindiso.booking.exception.NotFoundException;
import com.nsindiso.booking.exception.SlotUnavailableException;
import com.nsindiso.booking.repository.AppointmentHistoryRepository;
import com.nsindiso.booking.repository.AppointmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ManageBookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T06:00:00Z");
    private static final Instant START = Instant.parse("2026-10-29T08:00:00Z");
    private static final Instant NEW_START = Instant.parse("2026-10-30T09:00:00Z");
    private static final Slot NEW_SLOT = new Slot(NEW_START, NEW_START.plus(Duration.ofMinutes(30)));
    private static final String REF = "CAP-ABC234";
    private static final String EMAIL = "jane@example.com";

    @Mock AppointmentRepository appointments;
    @Mock AppointmentHistoryRepository history;
    @Mock SlotService slots;
    @Mock ApplicationEventPublisher events;

    private ManageBookingService service;
    private Appointment appointment;

    @BeforeEach
    void setUp () {
        BookingProperties props = new BookingProperties(Duration.ofHours(1), 60, Duration.ofHours(2), 2);
        service = new ManageBookingService(appointments, history, slots, props, events, Clock.fixed(NOW, ZoneOffset.UTC));
        appointment = Appointment.confirm(UUID.randomUUID(), REF, UUID.randomUUID(), UUID.randomUUID(),
                START, START.plus(Duration.ofMinutes(30)), "Jane Doe", EMAIL, "+27821234567", "k", NOW);
    }

    private void givenStored () {
        when(appointments.findByReference(REF)).thenReturn(Optional.of(appointment));
    }

    @Test
    void lookupNormalisesReferenceAndMatchesEmailCaseInsensitively () {
        givenStored();

        assertThat(service.lookup("  cap-abc234 ", "JANE@example.com")).isSameAs(appointment);
    }

    @Test
    void lookupWithWrongEmailIsNotFound () {
        givenStored();

        assertThatThrownBy(() -> service.lookup(REF, "someone@else.com"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void lookupOfUnknownReferenceIsNotFoundWithSameMessage () {
        givenStored();
        String wrongEmailMessage = catchMessage(() -> service.lookup(REF, "someone@else.com"));
        when(appointments.findByReference("CAP-ZZZZZZ")).thenReturn(Optional.empty());

        // Same response either way so references can't be enumerated.
        assertThat(catchMessage(() -> service.lookup("CAP-ZZZZZZ", EMAIL))).isEqualTo(wrongEmailMessage);
    }

    @Test
    void cancelRecordsHistoryAndPublishesEvent () {
        givenStored();

        Appointment result = service.cancel(REF, EMAIL);

        assertThat(result.getStatus()).isEqualTo(Appointment.Status.CANCELLED);
        AppointmentHistory h = captureHistory();
        assertThat(ReflectionTestUtils.getField(h, "eventType")).isEqualTo(AppointmentHistory.EventType.CANCELLED);
        assertThat(ReflectionTestUtils.getField(h, "oldStartAt")).isEqualTo(START);
        assertThat(ReflectionTestUtils.getField(h, "newStartAt")).isNull();
        verify(events).publishEvent(new AppointmentCancelled(appointment.getId()));
    }

    @Test
    void cancellingTwiceDoesNotRecordHistoryOrNotifyAgain () {
        givenStored();
        appointment.cancel(NOW);

        Appointment result = service.cancel(REF, EMAIL);

        assertThat(result.getStatus()).isEqualTo(Appointment.Status.CANCELLED);
        verifyNoInteractions(history, events);
    }

    @Test
    void cancelWithWrongEmailChangesNothing () {
        givenStored();

        assertThatThrownBy(() -> service.cancel(REF, "x@y.com")).isInstanceOf(NotFoundException.class);
        assertThat(appointment.getStatus()).isEqualTo(Appointment.Status.CONFIRMED);
        verifyNoInteractions(history, events);
    }

    @Test
    void rescheduleMovesToValidatedSlotAndRecordsOldAndNewTimes () {
        givenStored();
        when(slots.requireValidSlot(appointment.getBranchId(), NEW_START)).thenReturn(NEW_SLOT);

        Appointment result = service.reschedule(REF, EMAIL, NEW_START);

        assertThat(result.getStartAt()).isEqualTo(NEW_SLOT.start());
        assertThat(result.getEndAt()).isEqualTo(NEW_SLOT.end());
        assertThat(result.getRescheduleCount()).isEqualTo(1);
        verify(appointments).saveAndFlush(appointment);
        AppointmentHistory h = captureHistory();
        assertThat(ReflectionTestUtils.getField(h, "eventType")).isEqualTo(AppointmentHistory.EventType.RESCHEDULED);
        assertThat(ReflectionTestUtils.getField(h, "oldStartAt")).isEqualTo(START);
        assertThat(ReflectionTestUtils.getField(h, "newStartAt")).isEqualTo(NEW_START);
        verify(events).publishEvent(new AppointmentRescheduled(appointment.getId(), START));
    }

    @Test
    void rescheduleBeyondLimitIsRejectedBeforeCheckingSlots () {
        givenStored();
        appointment.setRescheduleCount(2);

        assertThatThrownBy(() -> service.reschedule(REF, EMAIL, NEW_START))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(slots, history, events);
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rescheduleToUnavailableSlotLeavesAppointmentUnchanged () {
        givenStored();
        when(slots.requireValidSlot(appointment.getBranchId(), NEW_START))
                .thenThrow(new SlotUnavailableException("taken"));

        assertThatThrownBy(() -> service.reschedule(REF, EMAIL, NEW_START))
                .isInstanceOf(SlotUnavailableException.class);
        assertThat(appointment.getStartAt()).isEqualTo(START);
        assertThat(appointment.getRescheduleCount()).isZero();
        verifyNoInteractions(history, events);
    }

    @Test
    void rescheduleRaceOnUniqueSlotIndexIsSlotUnavailable () {
        givenStored();
        when(slots.requireValidSlot(appointment.getBranchId(), NEW_START)).thenReturn(NEW_SLOT);
        when(appointments.saveAndFlush(appointment)).thenThrow(new DataIntegrityViolationException("uq_active_slot"));

        assertThatThrownBy(() -> service.reschedule(REF, EMAIL, NEW_START))
                .isInstanceOf(SlotUnavailableException.class);
        verifyNoInteractions(history, events);
    }

    private AppointmentHistory captureHistory () {
        ArgumentCaptor<AppointmentHistory> captor = ArgumentCaptor.forClass(AppointmentHistory.class);
        verify(history).save(captor.capture());
        return captor.getValue();
    }

    private static String catchMessage (Runnable r) {
        try {
            r.run();
        } catch (NotFoundException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected NotFoundException");
    }
}
