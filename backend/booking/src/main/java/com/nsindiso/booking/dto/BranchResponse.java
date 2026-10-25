package com.nsindiso.booking.dto;

import java.util.UUID;

public record BranchResponse(UUID id, String name, String address, String timezone) {
}
