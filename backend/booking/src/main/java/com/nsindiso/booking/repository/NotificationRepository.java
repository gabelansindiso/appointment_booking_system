package com.nsindiso.booking.repository;

import com.nsindiso.booking.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByAppointmentId(UUID appointmentId);
}
