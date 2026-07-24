package com.poshanforlife.api.dto;

import java.math.BigDecimal;

public record DocumentItemDto(
        String itemName,
        String description,
        String hsnSac,
        int quantity,
        BigDecimal rateInr,
        BigDecimal lineTotal) {
}
