package com.nsindiso.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.Branch;
import com.nsindiso.booking.entity.Notification;
import com.nsindiso.booking.entity.TestBranches;
import com.nsindiso.booking.enums.Channel;
import com.nsindiso.booking.enums.NotificationType;
import com.nsindiso.booking.repository.AppointmentRepository;
import com.nsindiso.booking.repository.BranchRepository;
import com.nsindiso.booking.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T06:00:00Z");
    private static final Instant START = Instant.parse("2026-10-29T08:00:00Z");   // Thu 10:00 SAST

    @Mock AppointmentRepository appointments;
    @Mock BranchRepository branches;
    @Mock NotificationRepository notifications;
    @Mock NotificationSender sender;

    private NotificationService service;
    private Appointment appointment;

    @BeforeEach
    void setUp () {
        service = new NotificationService(appointments, branches, notifications, sender, Clock.fixed(NOW, ZoneOffset.UTC));
        Branch branch = TestBranches.weekdayBranch(UUID.randomUUID(), 30);
        appointment = Appointment.confirm(UUID.randomUUID(), "CAP-ABC234", branch.getId(), UUID.randomUUID(),
                START, START.plus(Duration.ofMinutes(30)), "Jane  Mary Doe", "jane@example.com",
                "+27821234567", "k", NOW);
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
    }

    @Test
    void confirmationGoesToEmailAndSmsWithLocalTimeAndFirstName () {
        service.notifyCustomer(appointment.getId(), NotificationType.CONFIRMED, null);

        verify(sender).send(Channel.EMAIL, "jane@example.com", "Appointment confirmed: CAP-ABC234",
                "Hi Jane, your appointment at Stellenbosch Main is confirmed for Thu 29 Oct 2026 at 10:00. "
                        + "Your reference is CAP-ABC234. Please bring your ID.");
        verify(sender).send(Channel.SMS, "+27821234567", "Appointment confirmed: CAP-ABC234",
                "Stellenbosch Main: appointment confirmed for Thu 29 Oct 2026 at 10:00. Ref CAP-ABC234.");
    }

    @Test
    void rescheduleMessageIncludesPreviousAndNewTimes () {
        Instant previous = Instant.parse("2026-10-28T13:30:00Z");   // Wed 15:30 SAST

        service.notifyCustomer(appointment.getId(), NotificationType.RESCHEDULED, previous);

        verify(sender).send(eq(Channel.EMAIL), anyString(), eq("Appointment rescheduled: CAP-ABC234"),
                eq("Hi Jane, your appointment at Stellenbosch Main has moved from Wed 28 Oct 2026 at 15:30 "
                        + "to Thu 29 Oct 2026 at 10:00. Reference: CAP-ABC234."));
    }

    @Test
    void cancellationMessageMentionsOriginalTime () {
        service.notifyCustomer(appointment.getId(), NotificationType.CANCELLED, null);

        verify(sender).send(eq(Channel.SMS), anyString(), eq("Appointment cancelled: CAP-ABC234"),
                eq("Stellenbosch Main: appointment on Thu 29 Oct 2026 at 10:00 cancelled. Ref CAP-ABC234."));
    }

    @Test
    void storesSentNotificationsWithMaskedRecipients () {
        service.notifyCustomer(appointment.getId(), NotificationType.CONFIRMED, null);

        List<Notification> saved = captureSaved();
        assertThat(saved).extracting(Notification::getChannel).containsExactly(Channel.EMAIL, Channel.SMS);
        assertThat(saved).extracting(Notification::getRecipient).containsExactly("j***@example.com", "********4567");
        assertThat(saved).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(Notification.Status.SENT);
            assertThat(n.getAppointmentId()).isEqualTo(appointment.getId());
            assertThat(n.getType()).isEqualTo(NotificationType.CONFIRMED);
            assertThat(n.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void failedChannelIsRecordedAsFailedWithoutBlockingOtherChannel () {
        doThrow(new RuntimeException("SMTP down"))
                .when(sender).send(eq(Channel.EMAIL), any(), any(), any());

        service.notifyCustomer(appointment.getId(), NotificationType.CONFIRMED, null);

        verify(sender).send(eq(Channel.SMS), any(), any(), any());
        assertThat(captureSaved()).extracting(Notification::getChannel, Notification::getStatus).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Channel.EMAIL, Notification.Status.FAILED),
                org.assertj.core.groups.Tuple.tuple(Channel.SMS, Notification.Status.SENT));
    }

    private List<Notification> captureSaved () {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        return captor.getAllValues();
    }
}
