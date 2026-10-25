package com.nsindiso.booking.entity;

import com.nsindiso.booking.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Data;

// appointment/Appointment.java
@Entity
@Table(name = "appointment")
@Data
public class Appointment {

    public enum Status {CONFIRMED, CANCELLED, COMPLETED, NO_SHOW}

    @Id
    private UUID id;
    private String reference;
    @Column(name = "branch_id")
    private UUID branchId;
    @Column(name = "service_type_id")
    private UUID serviceTypeId;
    @Column(name = "start_at")
    private Instant startAt;
    @Column(name = "end_at")
    private Instant endAt;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "customer_email")
    private String customerEmail;
    @Column(name = "customer_phone")
    private String customerPhone;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "reschedule_count")
    private int rescheduleCount;
    @Column(name = "created_at")
    private Instant createdAt;

    @Version
    private Long version;   // wrapper type: null means "new" to Spring Data


    public static Appointment confirm (UUID id, String reference, UUID branchId, UUID serviceTypeId,
                                       Instant start, Instant end, String name, String email,
                                       String phone, String idempotencyKey, Instant now) {
        Appointment a = new Appointment();
        a.id = id;
        a.reference = reference;
        a.branchId = branchId;
        a.serviceTypeId = serviceTypeId;
        a.startAt = start;
        a.endAt = end;
        a.customerName = name;
        a.customerEmail = email;
        a.customerPhone = phone;
        a.status = Status.CONFIRMED;
        a.idempotencyKey = idempotencyKey;
        a.createdAt = now;
        return a;
    }

    /**
     * Constant-time; case and whitespace insensitive.
     */
    public boolean matchesEmail (String candidate) {
        if (candidate == null) return false;
        byte[] actual = customerEmail.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = candidate.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, supplied);
    }

    /**
     * @return true if state changed; false if already canceled (idempotent).
     */
    public boolean cancel (Instant now) {
        if (status == Status.CANCELLED) return false;
        if (status != Status.CONFIRMED) {
            throw new BusinessRuleException("Only confirmed appointments can be cancelled.");
        }
        if (!now.isBefore(startAt)) {
            throw new BusinessRuleException("Appointments that have already started can't be cancelled.");
        }
        status = Status.CANCELLED;
        return true;
    }

    public void ensureReschedulable (Instant newStart, Instant now, Duration cutoff, int maxReschedules) {
        if (status != Status.CONFIRMED) {
            throw new BusinessRuleException("Only confirmed appointments can be rescheduled.");
        }
        if (rescheduleCount >= maxReschedules) {
            throw new BusinessRuleException("This appointment has already been rescheduled the maximum number of times.");
        }
        if (now.plus(cutoff).isAfter(startAt)) {
            throw new BusinessRuleException("Appointments can't be rescheduled this close to their start time.");
        }
        if (newStart.equals(startAt)) {
            throw new BusinessRuleException("The new time is the same as the current time.");
        }
    }

    public void moveTo (Instant newStart, Instant newEnd) {
        this.startAt = newStart;
        this.endAt = newEnd;
        this.rescheduleCount++;
    }
}
