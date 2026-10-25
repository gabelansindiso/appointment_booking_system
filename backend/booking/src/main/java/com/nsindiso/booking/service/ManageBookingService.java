package com.nsindiso.booking.service;

import com.nsindiso.booking.config.BookingProperties;
import com.nsindiso.booking.dto.AppointmentCancelled;
import com.nsindiso.booking.dto.AppointmentRescheduled;
import com.nsindiso.booking.dto.Slot;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.AppointmentHistory;
import com.nsindiso.booking.exception.NotFoundException;
import com.nsindiso.booking.exception.SlotUnavailableException;
import com.nsindiso.booking.repository.AppointmentHistoryRepository;
import com.nsindiso.booking.repository.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageBookingService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final SlotService slotService;
    private final BookingProperties bookingProperties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ManageBookingService (AppointmentRepository appointmentRepository, AppointmentHistoryRepository appointmentHistoryRepository,
                                 SlotService slotService, BookingProperties bookingProperties,
                                 ApplicationEventPublisher events, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.slotService = slotService;
        this.bookingProperties = bookingProperties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Appointment lookup (String reference, String email) {
        return findOwned(reference, email);
    }

    @Transactional
    public Appointment cancel (String reference, String email) {
        Appointment appointment = findOwned(reference, email);
        Instant now = clock.instant();
        Instant start = appointment.getStartAt();

        if (appointment.cancel(now)) {   // false => already cancelled: no history, no second notification
            appointmentHistoryRepository.save(AppointmentHistory.of(appointment.getId(), AppointmentHistory.EventType.CANCELLED,
                    start, null, now
            ));
            events.publishEvent(new AppointmentCancelled(appointment.getId()));
        }
        return appointment;
    }

    @Transactional
    public Appointment reschedule (String reference, String email, Instant newStart) {
        Appointment appointment = findOwned(reference, email);
        Instant now = clock.instant();

        appointment.ensureReschedulable(newStart, now, bookingProperties.rescheduleCutoff(), bookingProperties.maxReschedules());
        Slot target = slotService.requireValidSlot(appointment.getBranchId(), newStart);

        Instant oldStart = appointment.getStartAt();
        appointment.moveTo(target.start(), target.end());
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new SlotUnavailableException("Slot unavailable");
        }

        appointmentHistoryRepository.save(AppointmentHistory.of(appointment.getId(), AppointmentHistory.EventType.RESCHEDULED,
                oldStart, target.start(), now
        ));
        events.publishEvent(new AppointmentRescheduled(appointment.getId(), oldStart));
        return appointment;
    }

    private Appointment findOwned (String reference, String email) {
        return appointmentRepository.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                .filter(a -> a.matchesEmail(email))
                .orElseThrow(() -> new NotFoundException("No booking found for that reference and email."));
    }
}
