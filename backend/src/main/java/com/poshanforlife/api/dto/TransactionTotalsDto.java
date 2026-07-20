package com.poshanforlife.api.dto;

import java.math.BigDecimal;

/**
 * Summary over the CURRENT FILTER SET (not just the current page).
 * totalCreditConsumed is reported as a non-negative figure — creditCharged on
 * each transaction is zero-or-negative (negative = credit consumed), so this
 * is the absolute value of that sum.
 */
public record TransactionTotalsDto(BigDecimal totalTransactionValue, BigDecimal totalCreditConsumed) {
}
