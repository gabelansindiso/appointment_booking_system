package com.nsindiso.booking.controller;

import com.nsindiso.booking.dto.Availability;
import com.nsindiso.booking.dto.AvailabilityResponse;
import com.nsindiso.booking.dto.Slot;
import com.nsindiso.booking.service.SlotService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/branches")
public class AvailabilityController {

    private final SlotService slotService;

    public AvailabilityController (SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/{branchId}/availability")
    AvailabilityResponse availability (@PathVariable UUID branchId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Availability availability = slotService.availability(branchId, date);

        return new AvailabilityResponse(availability.branch().getId(), availability.date(), availability.branch().getTimezone(),
                availability.branch().getSlotMinutes(),
                availability.slots().stream().map(s -> new Slot(s.start(), s.end())).toList()
        );
    }
}
