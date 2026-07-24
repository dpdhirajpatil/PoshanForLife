package com.poshanforlife.api.entity;

import java.math.BigDecimal;

/**
 * One row of a Document's line-item breakdown, persisted as part of the
 * {@code items} JSON array on {@link Document} (not its own table — line
 * items never need independent querying and always travel with their
 * document). hsnSac is the GST HSN/SAC classification code, optional since
 * not every service line has one on file yet.
 */
public record DocumentLineItem(
        String itemName,
        String description,
        String hsnSac,
        int quantity,
        BigDecimal rateInr) {

    public BigDecimal lineTotal() {
        return rateInr.multiply(BigDecimal.valueOf(quantity));
    }
}
