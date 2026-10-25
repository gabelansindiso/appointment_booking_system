package com.nsindiso.booking.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentRescheduled(UUID appointmentId, Instant previousStart) {
}
