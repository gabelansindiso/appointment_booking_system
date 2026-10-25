package com.nsindiso.booking.controller;

import com.nsindiso.booking.dto.BranchResponse;
import com.nsindiso.booking.dto.ServiceTypeResponse;
import com.nsindiso.booking.repository.BranchRepository;
import com.nsindiso.booking.repository.ServiceTypeRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final BranchRepository branchRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public CatalogController (BranchRepository branchRepository, ServiceTypeRepository serviceTypeRepository) {
        this.branchRepository = branchRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    @GetMapping("/branches")
    List<BranchResponse> branches () {
        return branchRepository.findAll().stream()
                .map(b -> new BranchResponse(b.getId(), b.getName(), b.getAddress(), b.getTimezone()))
                .toList();
    }

    @GetMapping("/service-types")
    List<ServiceTypeResponse> serviceTypes () {
        return serviceTypeRepository.findAll().stream()
                .map(s -> new ServiceTypeResponse(s.getCode(), s.getName())).toList();
    }

//    @GetMapping("/branches/{branchId}/availability")
//    AvailabilityResponse availability (@PathVariable UUID branchId,
//                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
//        Availability availability = slotService.availability(branchId, date);
//        return new AvailabilityResponse(availability.branch().getId(), availability.date(), availability.branch().getTimezone(),
//                availability.branch().getSlotMinutes(),
//                availability.slots().stream().map(s -> new Slot(s.start(), s.end())).toList()
//        );
//    }
}
