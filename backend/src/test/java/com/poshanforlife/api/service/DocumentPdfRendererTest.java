package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.Document;
import com.poshanforlife.api.entity.DocumentLineItem;
import com.poshanforlife.api.entity.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentPdfRendererTest {

    private final DocumentPdfRenderer renderer = new DocumentPdfRenderer();

    @Test
    void rendersAStructurallyValidSinglePagePdfWithExpectedContent() throws Exception {
        Document document = new Document();
        setId(document, UUID.randomUUID());
        setCreatedAt(document, Instant.parse("2026-07-24T10:00:00Z"));
        document.setDocumentType(DocumentType.ESTIMATE);
        document.setDocumentNumber("EST-202607-0001");
        document.setItems(List.of(
                new DocumentLineItem("12-week programme", "Weight loss", "9993", 1, new BigDecimal("15000.00"))));
        document.setDiscountInr(BigDecimal.ZERO);
        document.setValidForDays(7);
        document.setNotes("Please confirm by Friday");

        byte[] pdfBytes = renderer.render(document, "Jane Doe", "jane@example.com · 9000000000");

        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text)
                    .contains("ESTIMATE")
                    .contains("EST-202607-0001")
                    .contains("Jane Doe")
                    .contains("12-week programme")
                    .contains("9993")
                    .contains("CGST")
                    .contains("SGST")
                    .contains("Valid for 7 days")
                    .contains("Please confirm by Friday");
        }
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getSuperclass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static void setCreatedAt(Object entity, Instant instant) throws Exception {
        Field field = entity.getClass().getSuperclass().getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, instant);
    }
}
