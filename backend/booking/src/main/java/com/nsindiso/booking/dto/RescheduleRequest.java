package com.nsindiso.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RescheduleRequest(@NotBlank @Email @Size(max = 255) String email, @NotNull Instant newStart) {
}