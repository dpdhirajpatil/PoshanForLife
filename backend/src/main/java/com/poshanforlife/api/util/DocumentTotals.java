package com.poshanforlife.api.util;

import com.poshanforlife.api.entity.DocumentLineItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * subtotal/cgstAmount/sgstAmount/total for a Document — computed at read
 * time from items+discountInr (never persisted), so a future GST-rate change
 * doesn't need a data migration. GST is split evenly CGST/SGST per Indian
 * intra-state tax rules (2.5% + 2.5% = 5% total); this app doesn't yet model
 * inter-state IGST since Poshan for Life currently only serves customers
 * billed from the same state as the business.
 */
public record DocumentTotals(BigDecimal subtotal, BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal total) {

    private static final BigDecimal CGST_RATE = new BigDecimal("0.025");
    private static final BigDecimal SGST_RATE = new BigDecimal("0.025");

    public static DocumentTotals compute(List<DocumentLineItem> items, BigDecimal discountInr) {
        BigDecimal itemsTotal = items.stream()
                .map(DocumentLineItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = itemsTotal.subtract(discountInr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgst = subtotal.multiply(CGST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sgst = subtotal.multiply(SGST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(cgst).add(sgst);
        return new DocumentTotals(subtotal, cgst, sgst, total);
    }
}
