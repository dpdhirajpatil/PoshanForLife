package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Transaction> findByOrderIdInOrderByCreatedAtDesc(Collection<UUID> orderIds);

    boolean existsByOrderIdAndTransactionTypeNot(UUID orderId, TransactionType transactionType);

    /**
     * search must be non-null — pass "" for no filter (null String params in
     * lower()/concat() bind as bytea on Postgres). from/to are always set —
     * the service substitutes an open range. practitionerId/doctorId/
     * catalogueType/paymentType null = unfiltered. Keep the WHERE clause in
     * sync with {@link #sumTotals} below — same predicate, aggregated instead
     * of paged, so the summary reflects the whole filtered set.
     */
    @EntityGraph(attributePaths = {"patient", "createdBy", "order", "order.patientProgramme"})
    @Query("""
            select t from Transaction t
            where (:paymentType is null or t.paymentType = :paymentType)
              and (:catalogueType is null or (t.order.patientProgramme is not null
                   and t.order.patientProgramme.serviceType = :catalogueType))
              and (:practitionerId is null or t.createdBy.id = :practitionerId)
              and (:doctorId is null or exists (select 1 from DoctorPatient dp
                   where dp.doctor.id = :doctorId and dp.patient.id = t.patient.id))
              and (:search = ''
                   or lower(t.patient.name) like lower(concat('%', :search, '%'))
                   or lower(t.patient.email) like lower(concat('%', :search, '%')))
              and t.createdAt >= :from and t.createdAt < :to
            """)
    Page<Transaction> search(@Param("paymentType") PaymentType paymentType,
                             @Param("catalogueType") CatalogueItemType catalogueType,
                             @Param("practitionerId") UUID practitionerId,
                             @Param("doctorId") UUID doctorId,
                             @Param("search") String search,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);

    /** Same predicate as {@link #search} — keep the two in sync. */
    @Query("""
            select coalesce(sum(t.amountInr), 0) as totalAmount,
                   coalesce(sum(t.creditCharged), 0) as totalCredit
            from Transaction t
            where (:paymentType is null or t.paymentType = :paymentType)
              and (:catalogueType is null or (t.order.patientProgramme is not null
                   and t.order.patientProgramme.serviceType = :catalogueType))
              and (:practitionerId is null or t.createdBy.id = :practitionerId)
              and (:doctorId is null or exists (select 1 from DoctorPatient dp
                   where dp.doctor.id = :doctorId and dp.patient.id = t.patient.id))
              and (:search = ''
                   or lower(t.patient.name) like lower(concat('%', :search, '%'))
                   or lower(t.patient.email) like lower(concat('%', :search, '%')))
              and t.createdAt >= :from and t.createdAt < :to
            """)
    TransactionTotals sumTotals(@Param("paymentType") PaymentType paymentType,
                               @Param("catalogueType") CatalogueItemType catalogueType,
                               @Param("practitionerId") UUID practitionerId,
                               @Param("doctorId") UUID doctorId,
                               @Param("search") String search,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    interface TransactionTotals {
        BigDecimal getTotalAmount();

        BigDecimal getTotalCredit();
    }
}
