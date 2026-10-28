package com.nsindiso.booking.entity;

import com.nsindiso.booking.dto.OpeningHours;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Builds {@link Branch} instances for tests (its no-arg constructor is protected for JPA).
 */
public final class TestBranches {

    public static final String TIMEZONE = "Africa/Johannesburg";

    private TestBranches () {
    }

    /** Mon-Fri 08:30-17:00, Sat 08:30-12:30, closed Sunday. */
    public static Branch weekdayBranch (UUID id, int slotMinutes) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setName("Stellenbosch Main");
        branch.setAddress("1 Demo Street, Stellenbosch");
        branch.setTimezone(TIMEZONE);
        branch.setSlotMinutes(slotMinutes);
        for (int day = 1; day <= 5; day++) {
            branch.getOpeningHours().put(day, new OpeningHours(LocalTime.of(8, 30), LocalTime.of(17, 0)));
        }
        branch.getOpeningHours().put(6, new OpeningHours(LocalTime.of(8, 30), LocalTime.of(12, 30)));
        return branch;
    }
}
