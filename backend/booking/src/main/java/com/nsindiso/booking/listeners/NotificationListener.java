package com.nsindiso.booking.listeners;

import com.nsindiso.booking.dto.AppointmentCancelled;
import com.nsindiso.booking.dto.AppointmentConfirmed;
import com.nsindiso.booking.dto.AppointmentRescheduled;
import com.nsindiso.booking.enums.NotificationType;
import com.nsindiso.booking.service.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationListener {

    private final NotificationService service;

    public NotificationListener (NotificationService service) {
        this.service = service;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on (AppointmentConfirmed appointmentConfirmed) {
        service.notifyCustomer(appointmentConfirmed.appointmentId(), NotificationType.CONFIRMED, null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on (AppointmentRescheduled appointmentRescheduled) {
        service.notifyCustomer(appointmentRescheduled.appointmentId(), NotificationType.RESCHEDULED, appointmentRescheduled.previousStart());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on (AppointmentCancelled appointmentCancelled) {
        service.notifyCustomer(appointmentCancelled.appointmentId(), NotificationType.CANCELLED, null);
    }
}
