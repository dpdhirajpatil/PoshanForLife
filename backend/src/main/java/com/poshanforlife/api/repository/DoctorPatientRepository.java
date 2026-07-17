package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.DoctorPatient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DoctorPatientRepository extends JpaRepository<DoctorPatient, UUID> {

    List<DoctorPatient> findByDoctorId(UUID doctorId);

    @Modifying
    @Query("delete from DoctorPatient dp where dp.doctor.id = :doctorId")
    void deleteByDoctorId(UUID doctorId);
}
