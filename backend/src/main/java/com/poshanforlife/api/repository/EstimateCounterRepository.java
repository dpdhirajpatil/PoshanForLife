package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.EstimateCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EstimateCounterRepository extends JpaRepository<EstimateCounter, String> {

    /**
     * ON CONFLICT DO NOTHING instead of an insert-and-catch: a unique-key
     * violation would abort the surrounding Postgres transaction, and this
     * runs inside the caller's business transaction.
     */
    @Modifying
    @Query(value = "insert into estimate_counters (month_key, next_value) values (:monthKey, 1) "
            + "on conflict (month_key) do nothing", nativeQuery = true)
    void ensureCounterExists(@Param("monthKey") String monthKey);

    /** SELECT ... FOR UPDATE — serializes concurrent estimate-number generation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EstimateCounter c where c.monthKey = :monthKey")
    Optional<EstimateCounter> lockByMonthKey(@Param("monthKey") String monthKey);
}
