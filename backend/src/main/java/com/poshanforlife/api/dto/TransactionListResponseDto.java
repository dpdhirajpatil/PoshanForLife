package com.poshanforlife.api.dto;

import java.util.List;

/** The list endpoint's `data` payload: rows for the current page + totals over the whole filtered set. */
public record TransactionListResponseDto(
        List<TransactionListItemDto> transactions,
        TransactionTotalsDto summary) {
}
