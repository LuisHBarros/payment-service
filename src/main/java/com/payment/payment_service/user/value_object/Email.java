package com.payment.payment_service.user.value_object;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {
    private static final Pattern SIMPLE_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        Objects.requireNonNull(value, "email must not be null");
        value = value.trim().toLowerCase();

        if (!SIMPLE_EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid email");
        }
    }
}