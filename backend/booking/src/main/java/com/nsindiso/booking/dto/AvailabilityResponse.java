package com.nsindiso.booking.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(UUID branchId, LocalDate date, String timezone, int slotMinutes, List<Slot> slots) {
}
