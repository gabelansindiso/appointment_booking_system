package com.nsindiso.booking.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        String reference, UUID branchId, String branchName,
        String serviceTypeCode, String serviceTypeName,
        Instant start, Instant end, String timezone,
        String status, String customerName, int reschedulesRemaining) {
}
