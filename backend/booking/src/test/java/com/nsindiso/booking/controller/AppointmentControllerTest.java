package com.nsindiso.booking.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsindiso.booking.dto.AppointmentResponse;
import com.nsindiso.booking.dto.BookingResult;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.exception.BusinessRuleException;
import com.nsindiso.booking.exception.NotFoundException;
import com.nsindiso.booking.exception.SlotUnavailableException;
import com.nsindiso.booking.service.AppointmentAssembler;
import com.nsindiso.booking.service.BookingService;
import com.nsindiso.booking.service.ManageBookingService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
class AppointmentControllerTest {

    private static final String VALID_BOOKING = """
            {"branchId":"11111111-1111-1111-1111-111111111111","serviceType":"GENERAL_ENQUIRY",
             "start":"2026-10-29T08:00:00Z","customerName":"Jane Doe",
             "customerEmail":"jane@example.com","customerPhone":"+27821234567"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean BookingService booking;
    @MockitoBean ManageBookingService manage;
    @MockitoBean AppointmentAssembler assembler;

    private final Appointment appointment = new Appointment();

    @BeforeEach
    void setUp () {
        when(assembler.toResponse(any())).thenReturn(new AppointmentResponse("CAP-ABC234",
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "Stellenbosch Main",
                "GENERAL_ENQUIRY", "General enquiry", Instant.parse("2026-10-29T08:00:00Z"),
                Instant.parse("2026-10-29T08:30:00Z"), "Africa/Johannesburg", "CONFIRMED", "Jane Doe", 2));
    }

    @Test
    void newBookingReturns201WithLocation () throws Exception {
        when(booking.book(any(), eq("key-1"))).thenReturn(new BookingResult(appointment, false));

        mvc.perform(post("/api/appointments").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/appointments/CAP-ABC234"))
                .andExpect(jsonPath("$.reference").value("CAP-ABC234"))
                .andExpect(jsonPath("$.reschedulesRemaining").value(2));
    }

    @Test
    void replayedBookingReturns200 () throws Exception {
        when(booking.book(any(), eq("key-1"))).thenReturn(new BookingResult(appointment, true));

        mvc.perform(post("/api/appointments").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", "/api/appointments/CAP-ABC234"));
    }

    @Test
    void missingIdempotencyKeyIsRejected () throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(booking);
    }

    @Test
    void overlongIdempotencyKeyIsRejected () throws Exception {
        mvc.perform(post("/api/appointments").header("Idempotency-Key", "x".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));
        verifyNoInteractions(booking);
    }

    @Test
    void invalidBookingReturnsFieldErrors () throws Exception {
        String body = """
                {"branchId":"11111111-1111-1111-1111-111111111111","serviceType":"GENERAL_ENQUIRY",
                 "start":"2026-10-29T08:00:00Z","customerName":"",
                 "customerEmail":"not-an-email","customerPhone":"12ab"}
                """;

        mvc.perform(post("/api/appointments").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.customerEmail").exists())
                .andExpect(jsonPath("$.errors.customerPhone").value("must be 9-15 digits, optional leading +"));
        verifyNoInteractions(booking);
    }

    @Test
    void slotUnavailableMapsTo409 () throws Exception {
        when(booking.book(any(), anyString())).thenThrow(new SlotUnavailableException("Slot is unavailable"));

        mvc.perform(post("/api/appointments").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Slot is unavailable"));
    }

    @Test
    void lookupNotFoundMapsTo404 () throws Exception {
        when(manage.lookup("CAP-ABC234", "jane@example.com"))
                .thenThrow(new NotFoundException("No booking found for that reference and email."));

        mvc.perform(post("/api/appointments/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"CAP-ABC234\",\"email\":\"jane@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessRuleViolationMapsTo422 () throws Exception {
        when(manage.cancel("CAP-ABC234", "jane@example.com"))
                .thenThrow(new BusinessRuleException("Appointments that have already started can't be cancelled."));

        mvc.perform(post("/api/appointments/CAP-ABC234/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("already started")));
    }

    @Test
    void concurrentModificationMapsTo409 () throws Exception {
        when(manage.reschedule(eq("CAP-ABC234"), eq("jane@example.com"), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Appointment.class, "id"));

        mvc.perform(post("/api/appointments/CAP-ABC234/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"newStart\":\"2026-10-30T09:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("changed elsewhere")));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails () throws Exception {
        when(manage.cancel(anyString(), anyString())).thenThrow(new IllegalStateException("db password is hunter2"));

        mvc.perform(post("/api/appointments/CAP-ABC234/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again later."));
    }

    @Test
    void rescheduleRequiresNewStart () throws Exception {
        mvc.perform(post("/api/appointments/CAP-ABC234/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newStart").exists());
        verifyNoInteractions(manage);
    }
}
