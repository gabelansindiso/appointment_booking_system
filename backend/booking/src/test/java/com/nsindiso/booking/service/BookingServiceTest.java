package com.nsindiso.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nsindiso.booking.dto.AppointmentConfirmed;
import com.nsindiso.booking.dto.BookingResult;
import com.nsindiso.booking.dto.CreateAppointmentRequest;
import com.nsindiso.booking.dto.Slot;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.AppointmentHistory;
import com.nsindiso.booking.entity.ServiceType;
import com.nsindiso.booking.exception.BadRequestException;
import com.nsindiso.booking.exception.SlotUnavailableException;
import com.nsindiso.booking.repository.AppointmentHistoryRepository;
import com.nsindiso.booking.repository.AppointmentRepository;
import com.nsindiso.booking.repository.ServiceTypeRepository;
import java.sql.SQLException;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T06:00:00Z");
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-10-29T08:00:00Z");
    private static final Slot SLOT = new Slot(START, START.plus(Duration.ofMinutes(30)));
    private static final ServiceType GENERAL = new ServiceType(UUID.randomUUID(), "GENERAL_ENQUIRY", "General enquiry");
    private static final String KEY = "idem-123";

    @Mock AppointmentRepository appointments;
    @Mock AppointmentHistoryRepository history;
    @Mock ServiceTypeRepository serviceTypes;
    @Mock SlotService slots;
    @Mock ReferenceGenerator references;
    @Mock TransactionTemplate tx;
    @Mock ApplicationEventPublisher events;

    private BookingService service;

    @BeforeEach
    void setUp () {
        // Run the transactional callback inline.
        lenient().when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        service = new BookingService(appointments, history, serviceTypes, slots, references, tx, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateAppointmentRequest request () {
        return new CreateAppointmentRequest(BRANCH_ID, "GENERAL_ENQUIRY", START,
                "  Jane Doe ", " Jane.Doe@Example.COM ", "+27821234567");
    }

    private void givenValidBooking () {
        when(appointments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(serviceTypes.findByCode("GENERAL_ENQUIRY")).thenReturn(Optional.of(GENERAL));
        when(slots.requireValidSlot(BRANCH_ID, START)).thenReturn(SLOT);
        when(references.next()).thenReturn("CAP-ABC234");
    }

    @Test
    void createsConfirmedAppointmentWithNormalisedCustomerDetails () {
        givenValidBooking();

        BookingResult result = service.book(request(), KEY);

        assertThat(result.replayed()).isFalse();
        Appointment a = result.appointment();
        assertThat(a.getStatus()).isEqualTo(Appointment.Status.CONFIRMED);
        assertThat(a.getReference()).isEqualTo("CAP-ABC234");
        assertThat(a.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(a.getServiceTypeId()).isEqualTo(GENERAL.getId());
        assertThat(a.getStartAt()).isEqualTo(SLOT.start());
        assertThat(a.getEndAt()).isEqualTo(SLOT.end());
        assertThat(a.getCustomerName()).isEqualTo("Jane Doe");
        assertThat(a.getCustomerEmail()).isEqualTo("jane.doe@example.com");
        assertThat(a.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(a.getCreatedAt()).isEqualTo(NOW);
        verify(appointments).saveAndFlush(a);
    }

    @Test
    void recordsCreatedHistoryAndPublishesConfirmationEvent () {
        givenValidBooking();

        Appointment a = service.book(request(), KEY).appointment();

        ArgumentCaptor<AppointmentHistory> captor = ArgumentCaptor.forClass(AppointmentHistory.class);
        verify(history).save(captor.capture());
        AppointmentHistory h = captor.getValue();
        assertThat(ReflectionTestUtils.getField(h, "eventType")).isEqualTo(AppointmentHistory.EventType.CREATED);
        assertThat(ReflectionTestUtils.getField(h, "appointmentId")).isEqualTo(a.getId());
        assertThat(ReflectionTestUtils.getField(h, "oldStartAt")).isNull();
        assertThat(ReflectionTestUtils.getField(h, "newStartAt")).isEqualTo(START);
        verify(events).publishEvent(new AppointmentConfirmed(a.getId()));
    }

    @Test
    void returnsExistingAppointmentForRepeatedIdempotencyKey () {
        Appointment existing = new Appointment();
        when(appointments.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        BookingResult result = service.book(request(), KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.appointment()).isSameAs(existing);
        verifyNoInteractions(tx, slots, events);
    }

    @Test
    void regeneratesReferenceUntilUnique () {
        givenValidBooking();
        when(references.next()).thenReturn("CAP-AAAAAA", "CAP-BBBBBB", "CAP-CCCCCC");
        when(appointments.existsByReference("CAP-AAAAAA")).thenReturn(true);
        when(appointments.existsByReference("CAP-BBBBBB")).thenReturn(true);
        when(appointments.existsByReference("CAP-CCCCCC")).thenReturn(false);

        assertThat(service.book(request(), KEY).appointment().getReference()).isEqualTo("CAP-CCCCCC");
        verify(references, times(3)).next();
    }

    @Test
    void unknownServiceTypeIsBadRequest () {
        when(appointments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(serviceTypes.findByCode("GENERAL_ENQUIRY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(request(), KEY)).isInstanceOf(BadRequestException.class);
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void invalidSlotPropagatesAndNothingIsSaved () {
        when(appointments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(serviceTypes.findByCode("GENERAL_ENQUIRY")).thenReturn(Optional.of(GENERAL));
        when(slots.requireValidSlot(BRANCH_ID, START)).thenThrow(new SlotUnavailableException("nope"));

        assertThatThrownBy(() -> service.book(request(), KEY)).isInstanceOf(SlotUnavailableException.class);
        verify(appointments, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void losingTheRaceForASlotIsSlotUnavailable () {
        givenValidBooking();
        when(appointments.saveAndFlush(any())).thenThrow(constraintViolation("uq_active_slot"));

        assertThatThrownBy(() -> service.book(request(), KEY)).isInstanceOf(SlotUnavailableException.class);
        verifyNoInteractions(events);
    }

    @Test
    void concurrentRetryWithSameKeyReturnsTheWinningAppointment () {
        Appointment winner = new Appointment();
        when(appointments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty(), Optional.of(winner));
        when(serviceTypes.findByCode("GENERAL_ENQUIRY")).thenReturn(Optional.of(GENERAL));
        when(slots.requireValidSlot(BRANCH_ID, START)).thenReturn(SLOT);
        when(references.next()).thenReturn("CAP-ABC234");
        when(appointments.saveAndFlush(any())).thenThrow(constraintViolation("uq_appointment_idempotency_key"));

        BookingResult result = service.book(request(), KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.appointment()).isSameAs(winner);
    }

    @Test
    void unexplainedIntegrityViolationIsRethrown () {
        givenValidBooking();
        DataIntegrityViolationException violation = constraintViolation("some_other_constraint");
        when(appointments.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> service.book(request(), KEY)).isSameAs(violation);
    }

    private static DataIntegrityViolationException constraintViolation (String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"" + constraint + "\""));
    }
}
