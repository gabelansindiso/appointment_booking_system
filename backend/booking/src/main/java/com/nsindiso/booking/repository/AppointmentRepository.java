package com.nsindiso.booking.repository;

import com.nsindiso.booking.entity.Appointment;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    Optional<Appointment> findByReference(String reference);
    Optional<Appointment> findByIdempotencyKey(String key);
    boolean existsByReference(String reference);

    @Query("""
        select a.startAt from Appointment a
        where a.branchId = :branchId and a.status = :status
          and a.startAt >= :from and a.startAt < :to
        """)
    Set<Instant> findStartsBetween(@Param("branchId") UUID branchId,
                                   @Param("status") Appointment.Status status,
                                   @Param("from") Instant from, @Param("to") Instant to);
}
