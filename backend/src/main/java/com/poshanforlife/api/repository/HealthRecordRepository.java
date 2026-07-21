package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {

    List<HealthRecord> findByPatientIdOrderByRecordedAtDesc(UUID patientId);

    /** Upsert key for the InBody pipeline — one record per patient per calendar day. */
    Optional<HealthRecord> findByPatientIdAndRecordDate(UUID patientId, LocalDate recordDate);

    /** Newest record per patient (Postgres DISTINCT ON) — used for aggregate stats. */
    @Query(value = "SELECT DISTINCT ON (patient_id) * FROM health_records ORDER BY patient_id, recorded_at DESC",
            nativeQuery = true)
    List<HealthRecord> findLatestPerPatient();

    /** Every record on/after {@code from} — used for the dashboard's 6-month PBF trend. */
    List<HealthRecord> findByRecordDateGreaterThanEqual(LocalDate from);
}
