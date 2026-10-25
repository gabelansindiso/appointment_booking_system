package com.nsindiso.booking.service;

import com.nsindiso.booking.config.BookingProperties;
import com.nsindiso.booking.dto.AppointmentResponse;
import com.nsindiso.booking.entity.Appointment;
import com.nsindiso.booking.entity.Branch;
import com.nsindiso.booking.entity.ServiceType;
import com.nsindiso.booking.repository.BranchRepository;
import com.nsindiso.booking.repository.ServiceTypeRepository;
import org.springframework.stereotype.Component;

@Component
public class AppointmentAssembler {
    private final BranchRepository branchRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final BookingProperties bookingProperties;

    AppointmentAssembler (BranchRepository branchRepository, ServiceTypeRepository serviceTypeRepository,
                          BookingProperties bookingProperties) {
        this.branchRepository = branchRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.bookingProperties = bookingProperties;
    }

    public AppointmentResponse toResponse (Appointment appointment) {
        Branch branch = branchRepository.findById(appointment.getBranchId()).orElseThrow();
        ServiceType serviceType = serviceTypeRepository.findById(appointment.getServiceTypeId()).orElseThrow();
        return new AppointmentResponse(appointment.getReference(), branch.getId(), branch.getName(),
                serviceType.getCode(), serviceType.getName(), appointment.getStartAt(), appointment.getEndAt(), branch.getTimezone(),
                appointment.getStatus().name(), appointment.getCustomerName(),
                Math.max(0, bookingProperties.maxReschedules() - appointment.getRescheduleCount())
        );
    }
}
