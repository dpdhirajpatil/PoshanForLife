package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByPatientProgrammeId(UUID patientProgrammeId);

    List<Order> findByPatientProgrammeIdIn(Collection<UUID> patientProgrammeIds);
}
