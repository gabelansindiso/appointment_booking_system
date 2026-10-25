package com.nsindiso.booking.controller;

import com.nsindiso.booking.dto.AppointmentResponse;
import com.nsindiso.booking.dto.BookingResult;
import com.nsindiso.booking.dto.CreateAppointmentRequest;
import com.nsindiso.booking.dto.EmailRequest;
import com.nsindiso.booking.dto.LookupRequest;
import com.nsindiso.booking.dto.RescheduleRequest;
import com.nsindiso.booking.exception.BadRequestException;
import com.nsindiso.booking.service.AppointmentAssembler;
import com.nsindiso.booking.service.BookingService;
import com.nsindiso.booking.service.ManageBookingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final BookingService booking;
    private final ManageBookingService manage;
    private final AppointmentAssembler assembler;

    public AppointmentController(BookingService booking, ManageBookingService manage,
                                 AppointmentAssembler assembler) {
        this.booking = booking; this.manage = manage; this.assembler = assembler;
    }

    @PostMapping
    ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest req,
                                               @RequestHeader("Idempotency-Key") String key) {
        if (key.isBlank() || key.length() > 64) {
            throw new BadRequestException("Idempotency-Key must be 1-64 characters.");
        }
        BookingResult result = booking.book(req, key);
        AppointmentResponse body = assembler.toResponse(result.appointment());
        URI location = URI.create("/api/appointments/" + body.reference());
        return result.replayed()
                ? ResponseEntity.ok().location(location).body(body)        // 200: already processed
                : ResponseEntity.created(location).body(body);             // 201: newly created
    }

    @PostMapping("/lookup")
    AppointmentResponse lookup(@Valid @RequestBody LookupRequest req) {
        return assembler.toResponse(manage.lookup(req.reference(), req.email()));
    }

    @PostMapping("/{reference}/cancel")
    AppointmentResponse cancel(@PathVariable String reference, @Valid @RequestBody EmailRequest emailRequest) {
        return assembler.toResponse(manage.cancel(reference, emailRequest.email()));
    }

    @PostMapping("/{reference}/reschedule")
    AppointmentResponse reschedule(@PathVariable String reference,
                                   @Valid @RequestBody RescheduleRequest rescheduleRequest) {
        return assembler.toResponse(manage.reschedule(reference, rescheduleRequest.email(), rescheduleRequest.newStart()));
    }
}