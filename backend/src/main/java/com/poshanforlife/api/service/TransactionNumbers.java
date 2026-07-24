package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.EstimateCounter;
import com.poshanforlife.api.entity.InvoiceCounter;
import com.poshanforlife.api.repository.EstimateCounterRepository;
import com.poshanforlife.api.repository.InvoiceCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The reusable ID/invoice generators (shared by the assignment, orders,
 * transactions and documents features):
 *  - transactionId: "TRNID" + epoch-millis + 6 random alphanumerics,
 *    e.g. TRNID1749123456789AB2C3
 *  - invoiceNumber: "INV-{YYYYMM}-{NNNN}", sequence resets each calendar
 *    month; concurrency-safe via a per-month counter row locked with
 *    SELECT ... FOR UPDATE (no max+1 race). Documents of type invoice reuse
 *    this exact series (via nextInvoiceNumber, not a separate counter) so an
 *    invoice number is globally unique whether it originated as a
 *    Transaction or a Document.
 *  - estimateNumber: "EST-{YYYYMM}-{NNNN}", its own independent series
 *    (estimates are drafts, never legally the same series as real invoices).
 */
@Component
@RequiredArgsConstructor
public class TransactionNumbers {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final InvoiceCounterRepository invoiceCounterRepository;
    private final EstimateCounterRepository estimateCounterRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public String newTransactionId() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return "TRNID" + System.currentTimeMillis() + suffix;
    }

    /**
     * MANDATORY propagation: must run inside the caller's business
     * transaction so the row lock is held until that transaction commits and
     * the consumed number is rolled back together with the caller's writes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextInvoiceNumber() {
        String monthKey = LocalDate.now(ZoneOffset.UTC).format(MONTH_KEY);
        invoiceCounterRepository.ensureCounterExists(monthKey);
        InvoiceCounter counter = invoiceCounterRepository.lockByMonthKey(monthKey)
                .orElseThrow(() -> new IllegalStateException(
                        "invoice counter missing for month " + monthKey));
        int number = counter.getNextValue();
        counter.setNextValue(number + 1);
        return "INV-%s-%04d".formatted(monthKey, number);
    }

    /** Same locking scheme as {@link #nextInvoiceNumber()}, its own counter table/series. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextEstimateNumber() {
        String monthKey = LocalDate.now(ZoneOffset.UTC).format(MONTH_KEY);
        estimateCounterRepository.ensureCounterExists(monthKey);
        EstimateCounter counter = estimateCounterRepository.lockByMonthKey(monthKey)
                .orElseThrow(() -> new IllegalStateException(
                        "estimate counter missing for month " + monthKey));
        int number = counter.getNextValue();
        counter.setNextValue(number + 1);
        return "EST-%s-%04d".formatted(monthKey, number);
    }
}
