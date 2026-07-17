package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {

    List<HealthRecord> findByPatientIdOrderByRecordedAtDesc(UUID patientId);

    /** Newest record per patient (Postgres DISTINCT ON) — used for aggregate stats. */
    @Query(value = "SELECT DISTINCT ON (patient_id) * FROM health_records ORDER BY patient_id, recorded_at DESC",
            nativeQuery = true)
    List<HealthRecord> findLatestPerPatient();
}
