package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByPatientProgrammeId(UUID patientProgrammeId);

    List<Order> findByPatientProgrammeIdIn(Collection<UUID> patientProgrammeIds);

    /**
     * search must be non-null — pass "" for no filter (null String params in
     * lower()/concat() bind as bytea on Postgres). from/to are always set —
     * the service substitutes an open range, avoiding null-typed timestamp
     * params. doctorId null = unscoped (ADMIN).
     */
    @EntityGraph(attributePaths = {"patient", "patientProgramme"})
    @Query("""
            select o from Order o
            where (:status is null or o.status = :status)
              and (:paymentStatus is null or o.paymentStatus = :paymentStatus)
              and (:doctorId is null or exists (select 1 from DoctorPatient dp
                   where dp.doctor.id = :doctorId and dp.patient.id = o.patient.id))
              and (:search = ''
                   or lower(o.patient.name) like lower(concat('%', :search, '%'))
                   or lower(o.patient.email) like lower(concat('%', :search, '%')))
              and o.createdAt >= :from and o.createdAt < :to
            """)
    Page<Order> search(@Param("status") OrderStatus status,
                       @Param("paymentStatus") PaymentStatus paymentStatus,
                       @Param("doctorId") UUID doctorId,
                       @Param("search") String search,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       Pageable pageable);
}
