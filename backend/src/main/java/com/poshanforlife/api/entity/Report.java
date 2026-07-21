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

/**
 * A patient report: either a manual record (LAB/PRESCRIPTION/OTHER, no file)
 * or an uploaded InBody PDF processed by the AI extraction pipeline. fileUrl
 * holds the private-bucket OBJECT PATH, not a public URL — ReportService
 * mints a fresh signed URL from it on every read.
 */
@Getter
@Setter
@Entity
@Table(name = "reports")
public class Report extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportType type;

    @Column(length = 5000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_data")
    private InBodyData parsedData;

    @Column(length = 8)
    private String confidence;

    @Column(name = "extracted_field_count")
    private Integer extractedFieldCount;

    @Column(name = "extraction_method", length = 32)
    private String extractionMethod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
