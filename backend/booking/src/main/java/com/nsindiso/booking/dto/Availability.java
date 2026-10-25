package com.nsindiso.booking.dto;

import com.nsindiso.booking.entity.Branch;
import java.time.LocalDate;
import java.util.List;

public record Availability(Branch branch, LocalDate date, List<Slot> slots) {
}
