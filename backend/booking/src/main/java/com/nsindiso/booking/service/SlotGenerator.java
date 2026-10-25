package com.nsindiso.booking.service;

import com.nsindiso.booking.dto.Slot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SlotGenerator {

    public record DayHours(LocalTime open, LocalTime close) {}

    public List<Slot> generate(LocalDate date, DayHours hours, ZoneId zone, Duration slotLen,
                               Set<Instant> booked, Instant now, Duration minLead) {
        if (hours == null) return List.of();
        if (slotLen.isZero() || slotLen.isNegative()) {
            throw new IllegalArgumentException("slotLen must be positive");
        }
        Instant earliest = now.plus(minLead);
        ZonedDateTime cursor = ZonedDateTime.of(date, hours.open(), zone);
        ZonedDateTime close  = ZonedDateTime.of(date, hours.close(), zone);

        List<Slot> slots = new ArrayList<>();
        while (!cursor.plus(slotLen).isAfter(close)) {
            Instant start = cursor.toInstant();
            Instant end   = cursor.plus(slotLen).toInstant();
            if (!start.isBefore(earliest) && !booked.contains(start)) {
                slots.add(new Slot(start, end));
            }
            cursor = cursor.plus(slotLen);
        }
        return slots;
    }
}