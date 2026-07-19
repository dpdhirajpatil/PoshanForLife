package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-month invoice sequence counter (key = "YYYYMM"). Read and incremented
 * under a pessimistic lock so concurrent invoice generation can't produce
 * duplicate numbers. Deliberately not a BaseEntity — it's a counter row, not
 * a domain record.
 */
@Getter
@Setter
@Entity
@Table(name = "invoice_counters")
public class InvoiceCounter {

    @Id
    @Column(name = "month_key", length = 6)
    private String monthKey;

    @Column(name = "next_value", nullable = false)
    private int nextValue;
}
