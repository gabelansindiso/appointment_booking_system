package com.nsindiso.booking.dto;

import com.nsindiso.booking.entity.Appointment;

public record BookingResult(Appointment appointment, boolean replayed) {
}
