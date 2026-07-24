package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-month EST-{YYYYMM}-{NNNN} sequence counter, structurally identical to
 * {@link InvoiceCounter} but kept as a separate table/row rather than sharing
 * one: invoice numbers must stay a single globally-unique series across both
 * {@link Transaction#getInvoiceNumber()} and {@code Document} invoices (so a
 * Document invoice reuses {@link com.poshanforlife.api.service.TransactionNumbers#nextInvoiceNumber()}
 * directly), while estimate numbers are their own series that never needs to
 * interleave with real invoice numbers.
 */
@Getter
@Setter
@Entity
@Table(name = "estimate_counters")
public class EstimateCounter {

    @Id
    @Column(name = "month_key", length = 6)
    private String monthKey;

    @Column(name = "next_value", nullable = false)
    private int nextValue;
}
