package com.payment.payment_service.user.value_object;

import java.util.Objects;

public record Document(String value) {

    public Document {
        Objects.requireNonNull(value, "document must not be null");
        value = value.replaceAll("[.\\-/]", "").trim();

        if (!isValidCpf(value) && !isValidCnpj(value)) {
            throw new IllegalArgumentException("invalid document");
        }
    }

    public boolean isCpf() {
        return value.length() == 11;
    }

    public boolean isCnpj() {
        return value.length() == 14;
    }

    private static boolean isValidCpf(String v) {
        if (v.length() != 11 || v.chars().distinct().count() == 1) return false;
        return validateDigits(v, 9) && validateDigits(v, 10);
    }

    private static boolean isValidCnpj(String v) {
        if (v.length() != 14 || v.chars().distinct().count() == 1) return false;
        return validateCnpjDigit(v, 12) && validateCnpjDigit(v, 13);
    }

    private static boolean validateDigits(String cpf, int pos) {
        int sum = 0;
        for (int i = 0; i < pos; i++) sum += (cpf.charAt(i) - '0') * (pos + 1 - i);
        int digit = (sum * 10 % 11) % 10;
        return digit == (cpf.charAt(pos) - '0');
    }

    private static boolean validateCnpjDigit(String cnpj, int pos) {
        int[] weights = (pos == 12)
            ? new int[]{5,4,3,2,9,8,7,6,5,4,3,2}
            : new int[]{6,5,4,3,2,9,8,7,6,5,4,3,2};
        int sum = 0;
        for (int i = 0; i < pos; i++) sum += (cnpj.charAt(i) - '0') * weights[i];
        int digit = (sum % 11 < 2) ? 0 : 11 - (sum % 11);
        return digit == (cnpj.charAt(pos) - '0');
    }
    
    public String masked() {
        return isCpf()
            ? "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-" + value.substring(9)
            : "**." + value.substring(2, 5) + "." + value.substring(5, 8) + "/" + value.substring(8, 12) + "-" + value.substring(12);
    }
}   
