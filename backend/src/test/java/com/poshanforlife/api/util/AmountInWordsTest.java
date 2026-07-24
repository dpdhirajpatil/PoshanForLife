package com.poshanforlife.api.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @Test
    void wholeRupeesWithLakh() {
        assertThat(AmountInWords.convert(new BigDecimal("115826")))
                .isEqualTo("One Lakh Fifteen Thousand Eight Hundred Twenty-Six Rupees Only");
    }

    @Test
    void zeroIsZeroRupees() {
        assertThat(AmountInWords.convert(BigDecimal.ZERO)).isEqualTo("Zero Rupees Only");
    }

    @Test
    void includesPaiseWhenFractional() {
        assertThat(AmountInWords.convert(new BigDecimal("945.50")))
                .isEqualTo("Nine Hundred Forty-Five Rupees and Fifty Paise Only");
    }

    @Test
    void crorePlusLakhPlusThousand() {
        assertThat(AmountInWords.convert(new BigDecimal("12345678")))
                .isEqualTo("One Crore Twenty-Three Lakh Forty-Five Thousand Six Hundred Seventy-Eight Rupees Only");
    }
}
