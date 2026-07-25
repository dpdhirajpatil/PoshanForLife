package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Report;
import com.poshanforlife.api.entity.ReportStatus;
import com.poshanforlife.api.entity.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /**
     * search must be non-null — pass "" for no filter (null String params in
     * lower()/concat() bind as bytea on Postgres). status/type/doctorId/
     * patientId null = unfiltered. doctorId scopes a DOCTOR caller to their
     * assigned patients; patientId scopes a PATIENT caller to their own
     * reports only — callers pass at most one of the two (see
     * ReportService#list). Keep the WHERE clause in sync with
     * {@link #countStats} below — same predicate, aggregated instead of paged.
     */
    @EntityGraph(attributePaths = {"patient", "createdBy"})
    @Query("""
            select r from Report r
            where (:status is null or r.status = :status)
              and (:type is null or r.type = :type)
              and (:doctorId is null or exists (select 1 from DoctorPatient dp
                   where dp.doctor.id = :doctorId and dp.patient.id = r.patient.id))
              and (:patientId is null or r.patient.id = :patientId)
              and (:search = ''
                   or lower(r.title) like lower(concat('%', :search, '%'))
                   or lower(r.patient.name) like lower(concat('%', :search, '%'))
                   or lower(r.patient.email) like lower(concat('%', :search, '%')))
              and r.createdAt >= :from and r.createdAt < :to
            """)
    Page<Report> search(@Param("status") ReportStatus status,
                        @Param("type") ReportType type,
                        @Param("doctorId") UUID doctorId,
                        @Param("patientId") UUID patientId,
                        @Param("search") String search,
                        @Param("from") Instant from,
                        @Param("to") Instant to,
                        Pageable pageable);

    /**
     * Same predicate as {@link #search} — keep the two in sync. Status
     * constants are bound as parameters (rather than JPQL enum literals) to
     * stay on the same well-trodden binding style as the rest of the
     * repository layer.
     */
    @Query("""
            select
                count(r) as total,
                sum(case when r.status = :pending then 1 else 0 end) as pending,
                sum(case when r.status = :processing then 1 else 0 end) as processing,
                sum(case when r.status = :done then 1 else 0 end) as done,
                sum(case when r.status = :errorStatus then 1 else 0 end) as error,
                sum(case when r.createdAt >= :monthStart then 1 else 0 end) as thisMonth
            from Report r
            where (:status is null or r.status = :status)
              and (:type is null or r.type = :type)
              and (:doctorId is null or exists (select 1 from DoctorPatient dp
                   where dp.doctor.id = :doctorId and dp.patient.id = r.patient.id))
              and (:patientId is null or r.patient.id = :patientId)
              and (:search = ''
                   or lower(r.title) like lower(concat('%', :search, '%'))
                   or lower(r.patient.name) like lower(concat('%', :search, '%'))
                   or lower(r.patient.email) like lower(concat('%', :search, '%')))
              and r.createdAt >= :from and r.createdAt < :to
            """)
    ReportStats countStats(@Param("status") ReportStatus status,
                          @Param("type") ReportType type,
                          @Param("doctorId") UUID doctorId,
                          @Param("patientId") UUID patientId,
                          @Param("search") String search,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          @Param("monthStart") Instant monthStart,
                          @Param("pending") ReportStatus pending,
                          @Param("processing") ReportStatus processing,
                          @Param("done") ReportStatus done,
                          @Param("errorStatus") ReportStatus errorStatus);

    interface ReportStats {
        Long getTotal();

        Long getPending();

        Long getProcessing();

        Long getDone();

        Long getError();

        Long getThisMonth();
    }
}
