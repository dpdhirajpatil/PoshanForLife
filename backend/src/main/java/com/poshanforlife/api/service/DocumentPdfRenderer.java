package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.Document;
import com.poshanforlife.api.entity.DocumentLineItem;
import com.poshanforlife.api.entity.DocumentType;
import com.poshanforlife.api.util.AmountInWords;
import com.poshanforlife.api.util.DocumentTotals;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a single-page estimate/invoice PDF matching the printable
 * transaction invoice's layout (prompt 08's {@code invoice-page.component.ts},
 * client-side print CSS) — company header, bill-to, line-item table, GST
 * breakdown, amount in words, footer — but server-side with PDFBox, since
 * this document needs a durable {@code pdfUrl} the mobile app can open and
 * share directly, not a browser print dialog.
 */
@Service
public class DocumentPdfRenderer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    public byte[] render(Document document, String subjectName, String subjectContactLine) {
        DocumentTotals totals = DocumentTotals.compute(document.getItems(), document.getDiscountInr());
        boolean isEstimate = document.getDocumentType() == DocumentType.ESTIMATE;

        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                y = header(cs, regular, bold, document, isEstimate, y);
                y = billTo(cs, regular, bold, subjectName, subjectContactLine, y);
                y = lineItemsTable(cs, regular, bold, document.getItems(), y);
                y = breakdown(cs, regular, bold, totals, y);
                y = amountInWords(cs, italic, totals.total(), y);
                y = validityAndNotes(cs, regular, bold, document, isEstimate, y);
                footer(cs, regular, document, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render document PDF", e);
        }
    }

    private float header(PDPageContentStream cs, PDFont regular, PDFont bold,
                         Document document, boolean isEstimate, float y) throws IOException {
        text(cs, bold, 18, MARGIN, y, "Poshan for Life");
        text(cs, regular, 10, MARGIN, y - 16, "Nutrition & wellness services");

        String title = isEstimate ? "ESTIMATE" : "INVOICE";
        float titleWidth = bold.getStringWidth(title) / 1000 * 14;
        text(cs, bold, 14, PAGE_WIDTH - MARGIN - titleWidth, y, title);
        rightAligned(cs, regular, 10, y - 16, document.getDocumentNumber());
        rightAligned(cs, regular, 10, y - 30,
                document.getCreatedAt().atZone(ZoneOffset.UTC).format(DATE_FORMAT));

        float lineY = y - 42;
        cs.setLineWidth(1.2f);
        cs.moveTo(MARGIN, lineY);
        cs.lineTo(PAGE_WIDTH - MARGIN, lineY);
        cs.stroke();
        return lineY - 24;
    }

    private float billTo(PDPageContentStream cs, PDFont regular, PDFont bold,
                        String subjectName, String subjectContactLine, float y) throws IOException {
        text(cs, bold, 9, MARGIN, y, "BILL TO");
        text(cs, regular, 11, MARGIN, y - 16, subjectName);
        if (subjectContactLine != null && !subjectContactLine.isBlank()) {
            text(cs, regular, 9, MARGIN, y - 30, subjectContactLine);
            return y - 50;
        }
        return y - 40;
    }

    private float lineItemsTable(PDPageContentStream cs, PDFont regular, PDFont bold,
                                 List<DocumentLineItem> items, float y) throws IOException {
        float col1 = MARGIN;
        float col2 = MARGIN + 220;
        float col3 = MARGIN + 300;
        float col4 = MARGIN + 360;
        float col5 = PAGE_WIDTH - MARGIN - 70;

        text(cs, bold, 9, col1, y, "ITEM");
        text(cs, bold, 9, col2, y, "HSN/SAC");
        text(cs, bold, 9, col3, y, "QTY");
        text(cs, bold, 9, col4, y, "RATE");
        text(cs, bold, 9, col5, y, "AMOUNT");
        y -= 6;
        cs.setLineWidth(0.6f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(PAGE_WIDTH - MARGIN, y);
        cs.stroke();
        y -= 16;

        for (DocumentLineItem item : items) {
            text(cs, regular, 10, col1, y, item.itemName());
            if (item.description() != null && !item.description().isBlank()) {
                text(cs, regular, 8, col1, y - 12, item.description());
            }
            text(cs, regular, 10, col2, y, item.hsnSac() != null ? item.hsnSac() : "—");
            text(cs, regular, 10, col3, y, String.valueOf(item.quantity()));
            text(cs, regular, 10, col4, y, inr(item.rateInr()));
            text(cs, regular, 10, col5, y, inr(item.lineTotal()));
            y -= (item.description() != null && !item.description().isBlank()) ? 30 : 20;
        }
        return y - 10;
    }

    private float breakdown(PDPageContentStream cs, PDFont regular, PDFont bold,
                           DocumentTotals totals, float y) throws IOException {
        float labelX = PAGE_WIDTH - MARGIN - 180;
        y = breakdownRow(cs, regular, labelX, y, "Subtotal", inr(totals.subtotal()));
        y = breakdownRow(cs, regular, labelX, y, "CGST (2.5%)", inr(totals.cgstAmount()));
        y = breakdownRow(cs, regular, labelX, y, "SGST (2.5%)", inr(totals.sgstAmount()));
        cs.setLineWidth(0.8f);
        cs.moveTo(labelX, y + 6);
        cs.lineTo(PAGE_WIDTH - MARGIN, y + 6);
        cs.stroke();
        y -= 4;
        y = breakdownRow(cs, bold, labelX, y, "Total", inr(totals.total()));
        return y - 16;
    }

    private float breakdownRow(PDPageContentStream cs, PDFont font, float labelX, float y,
                              String label, String value) throws IOException {
        text(cs, font, 10, labelX, y, label);
        rightAligned(cs, font, 10, y, value);
        return y - 16;
    }

    private float amountInWords(PDPageContentStream cs, PDFont italic, BigDecimal total, float y) throws IOException {
        text(cs, italic, 9, MARGIN, y, AmountInWords.convert(total));
        return y - 24;
    }

    private float validityAndNotes(PDPageContentStream cs, PDFont regular, PDFont bold,
                                  Document document, boolean isEstimate, float y) throws IOException {
        if (isEstimate && document.getValidForDays() != null) {
            text(cs, regular, 9, MARGIN, y, "Valid for " + document.getValidForDays() + " days from issue.");
            y -= 18;
        }
        if (document.getNotes() != null && !document.getNotes().isBlank()) {
            text(cs, bold, 9, MARGIN, y, "NOTES");
            text(cs, regular, 9, MARGIN, y - 14, document.getNotes());
            y -= 34;
        }
        return y;
    }

    private void footer(PDPageContentStream cs, PDFont regular, Document document, float y) throws IOException {
        text(cs, regular, 8, MARGIN, MARGIN, "Document ID: " + document.getId());
        text(cs, regular, 8, MARGIN, MARGIN - 12,
                "This is a system-generated " + document.getDocumentType().toWire() + " from Poshan for Life.");
    }

    private static String inr(BigDecimal amount) {
        return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(value));
        cs.endText();
    }

    private void rightAligned(PDPageContentStream cs, PDFont font, float size, float y, String value) throws IOException {
        String sanitized = sanitize(value);
        float width = font.getStringWidth(sanitized) / 1000 * size;
        text(cs, font, size, PAGE_WIDTH - MARGIN - width, y, value);
    }

    /** PDFBox's standard fonts only encode WinAnsi — strip anything outside it (e.g. the ₹ sign) rather than throw. */
    private static String sanitize(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append(c < 256 ? c : '?');
        }
        return sb.toString();
    }
}
