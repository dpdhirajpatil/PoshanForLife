package com.poshanforlife.api.util;

import com.poshanforlife.api.entity.DocumentLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTotalsTest {

    @Test
    void splitsGstEvenlyBetweenCgstAndSgst() {
        List<DocumentLineItem> items = List.of(
                new DocumentLineItem("A", null, null, 2, new BigDecimal("500.00")));

        DocumentTotals totals = DocumentTotals.compute(items, BigDecimal.ZERO);

        assertThat(totals.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(totals.cgstAmount()).isEqualByComparingTo("25.00");
        assertThat(totals.sgstAmount()).isEqualByComparingTo("25.00");
        assertThat(totals.total()).isEqualByComparingTo("1050.00");
    }

    @Test
    void discountIsAppliedBeforeGst() {
        List<DocumentLineItem> items = List.of(
                new DocumentLineItem("A", null, null, 1, new BigDecimal("1000.00")));

        DocumentTotals totals = DocumentTotals.compute(items, new BigDecimal("200.00"));

        assertThat(totals.subtotal()).isEqualByComparingTo("800.00");
        assertThat(totals.cgstAmount()).isEqualByComparingTo("20.00");
        assertThat(totals.sgstAmount()).isEqualByComparingTo("20.00");
        assertThat(totals.total()).isEqualByComparingTo("840.00");
    }

    @Test
    void multipleLineItemsAreSummed() {
        List<DocumentLineItem> items = List.of(
                new DocumentLineItem("A", null, null, 1, new BigDecimal("300.00")),
                new DocumentLineItem("B", null, null, 3, new BigDecimal("100.00")));

        DocumentTotals totals = DocumentTotals.compute(items, BigDecimal.ZERO);

        assertThat(totals.subtotal()).isEqualByComparingTo("600.00");
    }
}
