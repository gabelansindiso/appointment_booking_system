package com.nsindiso.booking.entity;

import com.nsindiso.booking.enums.Channel;
import com.nsindiso.booking.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    public enum Status {SENT, FAILED}

    @Id
    private UUID id;
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    @Enumerated(EnumType.STRING)
    private Channel channel;
    private String recipient;   // masked
    private String body;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "created_at")
    private Instant createdAt;

    public static Notification of (UUID appointmentId, NotificationType type, Channel channel,
                                   String maskedRecipient, String body, Status status, Instant now) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.appointmentId = appointmentId;
        notification.type = type;
        notification.channel = channel;
        notification.recipient = maskedRecipient;
        notification.body = body;
        notification.status = status;
        notification.createdAt = now;
        return notification;
    }
}
