package com.nsindiso.booking.service;

import com.nsindiso.booking.config.BookingProperties;
import com.nsindiso.booking.dto.Availability;
import com.nsindiso.booking.dto.Slot;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.Branch;
import com.nsindiso.booking.exception.BadRequestException;
import com.nsindiso.booking.exception.NotFoundException;
import com.nsindiso.booking.exception.SlotUnavailableException;
import com.nsindiso.booking.repository.AppointmentRepository;
import com.nsindiso.booking.repository.BranchRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SlotService {

    private final SlotGenerator generator = new SlotGenerator();

    private final BranchRepository branches;
    private final AppointmentRepository appointments;
    private final BookingProperties props;
    private final Clock clock;

    @Autowired
    public SlotService(BranchRepository branches, AppointmentRepository appointments,
                       BookingProperties props, Clock clock) {
        this.branches = branches; this.appointments = appointments;
        this.props = props; this.clock = clock;
    }

    public Availability availability(UUID branchId, LocalDate date) {
        return compute(findBranch(branchId), date);
    }

    public Slot requireValidSlot(UUID branchId, Instant start) {
        Branch branch = findBranch(branchId);
        LocalDate localDate = start.atZone(branch.zone()).toLocalDate();
        return compute(branch, localDate).slots().stream()
                .filter(s -> s.start().equals(start))
                .findFirst()
                .orElseThrow(() -> new SlotUnavailableException("That time slot is not available."));
    }

    private Availability compute(Branch branch, LocalDate date) {
        ZoneId zone = branch.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isBefore(today) || date.isAfter(today.plusDays(props.maxAdvanceDays()))) {
            throw new BadRequestException("Date is outside the bookable range.");
        }
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to   = date.plusDays(1).atStartOfDay(zone).toInstant();
        Set<Instant> booked = appointments.findStartsBetween(
                branch.getId(), Appointment.Status.CONFIRMED, from, to);

        List<Slot> slots = generator.generate(date, branch.hoursFor(date.getDayOfWeek()), zone,
                Duration.ofMinutes(branch.getSlotMinutes()), booked, clock.instant(), props.minLeadTime());
        return new Availability(branch, date, slots);
    }

    private Branch findBranch(UUID id) {
        return branches.findById(id).orElseThrow(() -> new NotFoundException("Branch not found."));
    }
}
