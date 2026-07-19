package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.InvoiceCounter;
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
 * The reusable ID/invoice generators (shared by the assignment, orders and
 * transactions features):
 *  - transactionId: "TRNID" + epoch-millis + 6 random alphanumerics,
 *    e.g. TRNID1749123456789AB2C3
 *  - invoiceNumber: "INV-{YYYYMM}-{NNNN}", sequence resets each calendar
 *    month; concurrency-safe via a per-month counter row locked with
 *    SELECT ... FOR UPDATE (no max+1 race).
 */
@Component
@RequiredArgsConstructor
public class TransactionNumbers {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final InvoiceCounterRepository invoiceCounterRepository;

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
}
