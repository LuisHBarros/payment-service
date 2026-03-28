package com.payment.payment_service.user.type;

import com.payment.payment_service.user.value_object.Document;

public enum UserType {
    COMMON,
    MERCHANT,
    ADMIN;

    public static UserType from(Document document) {
        return document.isCpf() ? COMMON : MERCHANT;
    }

}
