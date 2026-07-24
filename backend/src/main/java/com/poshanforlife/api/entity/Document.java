package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

/**
 * A draft estimate or a GST-aware invoice — extends prompt 08's
 * Transaction/invoice-number machinery with a line-item breakdown for the
 * mobile app's RN-20 module. Exactly one of lead/patient is set (DB CHECK
 * constraint, same pattern as {@link com.poshanforlife.api.entity.PatientProgramme}'s
 * exactly-one-item rule): an estimate is usually for a not-yet-converted
 * Lead, an invoice usually for a Patient, but nothing stops either type
 * pointing at either subject. subtotal/cgstAmount/sgstAmount/total are
 * deliberately NOT persisted — see DocumentService, computed at read time
 * from items+discountInr so a future GST-rate change doesn't require a data
 * migration.
 */
@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 16)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, unique = true, length = 20)
    private String documentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private User patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<DocumentLineItem> items;

    @Column(name = "discount_inr", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountInr = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    /** Estimates only — how many days the quoted price is held for; null for invoices. */
    @Column(name = "valid_for_days")
    private Integer validForDays;

    /** Supabase Storage object path of the rendered PDF, cached after first render. */
    @Column(name = "pdf_object_path", length = 500)
    private String pdfObjectPath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
