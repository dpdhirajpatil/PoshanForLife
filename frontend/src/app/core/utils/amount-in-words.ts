/**
 * Converts a rupee amount to words using the Indian numbering system
 * (lakh/crore), e.g. 115826 → "One Lakh Fifteen Thousand Eight Hundred
 * Twenty-Six Rupees Only". Used by the printable invoice.
 */

const ONES = [
  '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine',
  'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
  'Seventeen', 'Eighteen', 'Nineteen',
];
const TENS = [
  '', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety',
];

/** 0-999 → words, no trailing/leading space. */
function threeDigitsToWords(n: number): string {
  if (n === 0) return '';
  if (n < 20) return ONES[n];
  if (n < 100) {
    const tens = TENS[Math.floor(n / 10)];
    const ones = n % 10;
    return ones ? `${tens}-${ONES[ones]}` : tens;
  }
  const hundreds = Math.floor(n / 100);
  const rest = n % 100;
  return rest ? `${ONES[hundreds]} Hundred ${threeDigitsToWords(rest)}` : `${ONES[hundreds]} Hundred`;
}

/** Non-negative integer → words using crore/lakh/thousand/hundred grouping. */
function integerToWords(value: number): string {
  if (value === 0) return 'Zero';

  const crore = Math.floor(value / 1_00_00_000);
  const lakh = Math.floor((value % 1_00_00_000) / 1_00_000);
  const thousand = Math.floor((value % 1_00_000) / 1_000);
  const hundred = value % 1_000;

  const parts: string[] = [];
  if (crore) parts.push(`${threeDigitsToWords(crore)} Crore`);
  if (lakh) parts.push(`${threeDigitsToWords(lakh)} Lakh`);
  if (thousand) parts.push(`${threeDigitsToWords(thousand)} Thousand`);
  if (hundred) parts.push(threeDigitsToWords(hundred));
  return parts.join(' ');
}

/** e.g. amountInWords(115826) → "One Lakh Fifteen Thousand Eight Hundred Twenty-Six Rupees Only". */
export function amountInWords(amount: number): string {
  const rupees = Math.floor(Math.abs(amount));
  const paise = Math.round((Math.abs(amount) - rupees) * 100);

  let words = `${integerToWords(rupees)} Rupees`;
  if (paise > 0) {
    words += ` and ${integerToWords(paise)} Paise`;
  }
  return `${words} Only`;
}
