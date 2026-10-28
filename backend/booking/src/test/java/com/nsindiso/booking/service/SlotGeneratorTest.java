package com.nsindiso.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsindiso.booking.dto.Slot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlotGeneratorTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");   // UTC+2, no DST
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final SlotGenerator.DayHours NINE_TO_ELEVEN =
            new SlotGenerator.DayHours(LocalTime.of(9, 0), LocalTime.of(11, 0));
    private static final Instant LONG_AGO = Instant.parse("2026-01-01T00:00:00Z");

    private final SlotGenerator generator = new SlotGenerator();

    @Test
    void generatesBackToBackSlotsBetweenOpenAndClose () {
        List<Slot> slots = generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ofMinutes(30),
                Set.of(), LONG_AGO, Duration.ZERO);

        assertThat(slots).extracting(Slot::start).containsExactly(
                Instant.parse("2026-11-02T07:00:00Z"),
                Instant.parse("2026-11-02T07:30:00Z"),
                Instant.parse("2026-11-02T08:00:00Z"),
                Instant.parse("2026-11-02T08:30:00Z"));
        assertThat(slots).allSatisfy(s -> assertThat(Duration.between(s.start(), s.end())).hasMinutes(30));
    }

    @Test
    void dropsTrailingSlotThatWouldOverrunClosingTime () {
        List<Slot> slots = generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ofMinutes(45),
                Set.of(), LONG_AGO, Duration.ZERO);

        // 09:00, 09:45 fit; 10:30-11:15 would overrun.
        assertThat(slots).hasSize(2);
        assertThat(slots.getLast().end()).isEqualTo(Instant.parse("2026-11-02T08:30:00Z"));
    }

    @Test
    void excludesBookedSlots () {
        Instant booked = Instant.parse("2026-11-02T07:30:00Z");

        List<Slot> slots = generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ofMinutes(30),
                Set.of(booked), LONG_AGO, Duration.ZERO);

        assertThat(slots).hasSize(3).extracting(Slot::start).doesNotContain(booked);
    }

    @Test
    void excludesSlotsInsideMinimumLeadTime () {
        Instant now = Instant.parse("2026-11-02T06:45:00Z");   // 08:45 local

        List<Slot> slots = generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ofMinutes(30),
                Set.of(), now, Duration.ofHours(1));

        // earliest bookable is 09:45 local -> first slot 10:00 local
        assertThat(slots).extracting(Slot::start).containsExactly(
                Instant.parse("2026-11-02T08:00:00Z"),
                Instant.parse("2026-11-02T08:30:00Z"));
    }

    @Test
    void slotStartingExactlyAtEarliestBookableTimeIsIncluded () {
        Instant now = Instant.parse("2026-11-02T06:00:00Z");   // 08:00 local, +1h = 09:00 local

        List<Slot> slots = generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ofMinutes(30),
                Set.of(), now, Duration.ofHours(1));

        assertThat(slots.getFirst().start()).isEqualTo(Instant.parse("2026-11-02T07:00:00Z"));
    }

    @Test
    void returnsNoSlotsWhenClosed () {
        assertThat(generator.generate(MONDAY, null, SAST, Duration.ofMinutes(30),
                Set.of(), LONG_AGO, Duration.ZERO)).isEmpty();
    }

    @Test
    void rejectsNonPositiveSlotLength () {
        assertThatThrownBy(() -> generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ZERO,
                Set.of(), LONG_AGO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(MONDAY, NINE_TO_ELEVEN, SAST, Duration.ofMinutes(-30),
                Set.of(), LONG_AGO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesWallClockHoursAcrossDaylightSavingTransition () {
        ZoneId london = ZoneId.of("Europe/London");
        LocalDate dstStart = LocalDate.of(2026, 3, 29);   // clocks go forward at 01:00 UTC

        List<Slot> slots = generator.generate(dstStart, NINE_TO_ELEVEN, london, Duration.ofHours(1),
                Set.of(), LONG_AGO, Duration.ZERO);

        // 09:00 BST == 08:00 UTC
        assertThat(slots).extracting(Slot::start).containsExactly(
                Instant.parse("2026-03-29T08:00:00Z"),
                Instant.parse("2026-03-29T09:00:00Z"));
    }
}
