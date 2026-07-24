package com.poshanforlife.api.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a rupee amount to words using the Indian numbering system
 * (lakh/crore), e.g. 115826 -&gt; "One Lakh Fifteen Thousand Eight Hundred
 * Twenty-Six Rupees Only". Java port of the frontend's identically-named
 * {@code amount-in-words.ts} (used by the printable transaction invoice) —
 * kept in sync deliberately, since DocumentPdfRenderer needs the same line
 * server-side for the generated estimate/invoice PDF.
 */
public final class AmountInWords {

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen",
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
    };

    private AmountInWords() {
    }

    public static String convert(BigDecimal amount) {
        BigDecimal abs = amount.abs();
        long rupees = abs.longValue();
        int paise = abs.subtract(BigDecimal.valueOf(rupees))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        StringBuilder words = new StringBuilder(integerToWords(rupees)).append(" Rupees");
        if (paise > 0) {
            words.append(" and ").append(integerToWords(paise)).append(" Paise");
        }
        return words.append(" Only").toString();
    }

    private static String integerToWords(long value) {
        if (value == 0) {
            return "Zero";
        }
        long crore = value / 1_00_00_000;
        long lakh = (value % 1_00_00_000) / 1_00_000;
        long thousand = (value % 1_00_000) / 1_000;
        long hundred = value % 1_000;

        StringBuilder parts = new StringBuilder();
        if (crore > 0) {
            appendPart(parts, threeDigitsToWords(crore) + " Crore");
        }
        if (lakh > 0) {
            appendPart(parts, threeDigitsToWords(lakh) + " Lakh");
        }
        if (thousand > 0) {
            appendPart(parts, threeDigitsToWords(thousand) + " Thousand");
        }
        if (hundred > 0) {
            appendPart(parts, threeDigitsToWords(hundred));
        }
        return parts.toString();
    }

    private static void appendPart(StringBuilder parts, String part) {
        if (!parts.isEmpty()) {
            parts.append(' ');
        }
        parts.append(part);
    }

    /** 0-999 -> words, no trailing/leading space. */
    private static String threeDigitsToWords(long n) {
        if (n == 0) {
            return "";
        }
        if (n < 20) {
            return ONES[(int) n];
        }
        if (n < 100) {
            String tens = TENS[(int) (n / 10)];
            long ones = n % 10;
            return ones > 0 ? tens + "-" + ONES[(int) ones] : tens;
        }
        long hundreds = n / 100;
        long rest = n % 100;
        String prefix = ONES[(int) hundreds] + " Hundred";
        return rest > 0 ? prefix + " " + threeDigitsToWords(rest) : prefix;
    }
}
