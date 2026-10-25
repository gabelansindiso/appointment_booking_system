package com.nsindiso.booking.service;

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
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private final AppointmentRepository appointments;
    private final AppointmentHistoryRepository history;
    private final ServiceTypeRepository serviceTypes;
    private final SlotService slots;
    private final ReferenceGenerator references;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(AppointmentRepository appointments, AppointmentHistoryRepository history,
                          ServiceTypeRepository serviceTypes, SlotService slots,
                          ReferenceGenerator references, TransactionTemplate tx,
                          ApplicationEventPublisher events, Clock clock) {
        this.appointments = appointments; this.history = history; this.serviceTypes = serviceTypes;
        this.slots = slots; this.references = references; this.tx = tx;
        this.events = events; this.clock = clock;
    }

    public BookingResult book(CreateAppointmentRequest req, String idempotencyKey) {
        Optional<Appointment> existing = appointments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return new BookingResult(existing.get(), true);

        try {
            Appointment created = tx.execute(status -> create(req, idempotencyKey));
            return new BookingResult(created, false);
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (cause.contains("uq_active_slot")) {
                throw new SlotUnavailableException("Slot is unavailable");            // lost the race for the slot
            }
            // Otherwise: two retries of the same request raced on the idempotency key.
            return appointments.findByIdempotencyKey(idempotencyKey)
                    .map(a -> new BookingResult(a, true))
                    .orElseThrow(() -> e);
        }
    }

    private Appointment create(CreateAppointmentRequest req, String key) {
        ServiceType type = serviceTypes.findByCode(req.serviceType())
                .orElseThrow(() -> new BadRequestException("Unknown service type."));
        Slot slot = slots.requireValidSlot(req.branchId(), req.start());
        Instant now = clock.instant();

        Appointment appt = Appointment.confirm(
                UUID.randomUUID(), newReference(), req.branchId(), type.getId(),
                slot.start(), slot.end(),
                req.customerName().trim(), req.customerEmail().trim().toLowerCase(Locale.ROOT),
                req.customerPhone(), key, now);

        appointments.saveAndFlush(appt);   // flush: the unique index fires HERE, inside the try
        history.save(AppointmentHistory.of(appt.getId(), AppointmentHistory.EventType.CREATED,
                null, appt.getStartAt(), now));
        events.publishEvent(new AppointmentConfirmed(appt.getId()));
        return appt;
    }

    private String newReference() {
        String ref;
        do { ref = references.next(); } while (appointments.existsByReference(ref));
        return ref;
    }
}
