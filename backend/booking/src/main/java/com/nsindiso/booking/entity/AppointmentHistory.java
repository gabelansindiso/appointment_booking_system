package com.nsindiso.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "appointment_history")
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentHistory {
    public enum EventType {CREATED, RESCHEDULED, CANCELLED}

    @Id
    private UUID id;
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private EventType eventType;
    @Column(name = "old_start_at")
    private Instant oldStartAt;
    @Column(name = "new_start_at")
    private Instant newStartAt;
    @Column(name = "occurred_at")
    private Instant occurredAt;

    public static AppointmentHistory of (UUID appointmentId, EventType type,
                                         Instant oldStart, Instant newStart, Instant now) {
        var h = new AppointmentHistory();
        h.id = UUID.randomUUID();
        h.appointmentId = appointmentId;
        h.eventType = type;
        h.oldStartAt = oldStart;
        h.newStartAt = newStart;
        h.occurredAt = now;
        return h;
    }
}
