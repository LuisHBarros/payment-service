package com.payment.payment_service.user.value_object;

import java.util.Objects;

public record Password(String value) {

    public Password {
        Objects.requireNonNull(value, "password must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }

        if (value.length() < 5) {
            throw new IllegalArgumentException("password must have at least 5 characters");
        }
    }
}
