package com.nsindiso.booking.service;

import com.nsindiso.booking.common.Masking;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.Branch;
import com.nsindiso.booking.entity.Notification;
import com.nsindiso.booking.enums.Channel;
import com.nsindiso.booking.enums.NotificationType;
import com.nsindiso.booking.repository.AppointmentRepository;
import com.nsindiso.booking.repository.BranchRepository;
import com.nsindiso.booking.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private record Message(String subject, String emailBody, String smsBody) {}

    private final AppointmentRepository appointmentRepository;
    private final BranchRepository branchRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public NotificationService(AppointmentRepository appointmentRepository, BranchRepository branchRepository,
                               NotificationRepository notificationRepository, NotificationSender notificationSender, Clock clock) {
        this.appointmentRepository = appointmentRepository; this.branchRepository = branchRepository;
        this.notificationRepository = notificationRepository; this.notificationSender = notificationSender; this.clock = clock;
    }

    @Transactional
    public void notifyCustomer(UUID appointmentId, NotificationType type, Instant previousStart) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        Branch branch = branchRepository.findById(appointment.getBranchId()).orElseThrow();
        Message message = compose(type, appointment, branch, previousStart);

        deliver(appointment, type, Channel.EMAIL, appointment.getCustomerEmail(), Masking.email(appointment.getCustomerEmail()),
                message.subject(), message.emailBody());
        deliver(appointment, type, Channel.SMS, appointment.getCustomerPhone(), Masking.phone(appointment.getCustomerPhone()),
                message.subject(), message.smsBody());
    }

    private void deliver(Appointment appointment, NotificationType type, Channel channel,
                         String recipient, String masked, String subject, String body) {
        Notification.Status status;
        try {
            notificationSender.send(channel, recipient, subject, body);
            status = Notification.Status.SENT;
        } catch (RuntimeException e) {
            log.warn("Failed to send {} {} for {}: {}", channel, type, appointment.getReference(), e.getMessage());
            status = Notification.Status.FAILED;   // appointment failed notification never fails the booking
        }
        notificationRepository.save(Notification.of(appointment.getId(), type, channel, masked, body, status, clock.instant()));
    }

    private Message compose(NotificationType type, Appointment appointment, Branch branch, Instant previousStart) {
        ZoneId zone = branch.zone();
        String when = FMT.format(appointment.getStartAt().atZone(zone));
        String first = appointment.getCustomerName().trim().split("\\s+")[0];
        String ref = appointment.getReference();

        return switch (type) {
            case CONFIRMED -> new Message(
                    "Appointment confirmed: " + ref,
                    "Hi %s, your appointment at %s is confirmed for %s. Your reference is %s. Please bring your ID."
                            .formatted(first, branch.getName(), when, ref),
                    "%s: appointment confirmed for %s. Ref %s.".formatted(branch.getName(), when, ref));
            case RESCHEDULED -> {
                String old = FMT.format(previousStart.atZone(zone));
                yield new Message(
                        "Appointment rescheduled: " + ref,
                        "Hi %s, your appointment at %s has moved from %s to %s. Reference: %s."
                                .formatted(first, branch.getName(), old, when, ref),
                        "%s: appointment moved to %s. Ref %s.".formatted(branch.getName(), when, ref));
            }
            case CANCELLED -> new Message(
                    "Appointment cancelled: " + ref,
                    "Hi %s, your appointment at %s on %s has been cancelled. You're welcome to book again any time."
                            .formatted(first, branch.getName(), when),
                    "%s: appointment on %s cancelled. Ref %s.".formatted(branch.getName(), when, ref));
        };
    }
}
