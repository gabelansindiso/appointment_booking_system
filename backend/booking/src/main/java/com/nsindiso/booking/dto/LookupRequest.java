package com.nsindiso.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LookupRequest(@NotBlank @Size(max = 20) String reference,
                            @NotBlank @Email @Size(max = 255) String email) {}
