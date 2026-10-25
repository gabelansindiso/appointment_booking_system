package com.nsindiso.booking.entity;

import com.nsindiso.booking.dto.OpeningHours;
import com.nsindiso.booking.service.SlotGenerator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "branch")
@Data
public class Branch {
    @Id
    private UUID id;
    private String name;
    private String address;
    private String timezone;
    @Column(name = "slot_minutes")
    private int slotMinutes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "branch_opening_hours", joinColumns = @JoinColumn(name = "branch_id"))
    @MapKeyColumn(name = "day_of_week")
    private Map<Integer, OpeningHours> openingHours = new HashMap<>();

    protected Branch () {
    }

    public ZoneId zone () {
        return ZoneId.of(timezone);
    }

    public SlotGenerator.DayHours hoursFor (DayOfWeek day) {
        OpeningHours h = openingHours.get(day.getValue());
        return h == null ? null : new SlotGenerator.DayHours(h.open(), h.close());
    }
}
