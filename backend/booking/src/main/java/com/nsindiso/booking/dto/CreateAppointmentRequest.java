package com.nsindiso.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID branchId,
        @NotBlank String serviceType,
        @NotNull Instant start,
        @NotBlank @Size(max = 100) String customerName,
        @NotBlank @Email @Size(max = 255) String customerEmail,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "must be 9-15 digits, optional leading +")
        String customerPhone) {
}
