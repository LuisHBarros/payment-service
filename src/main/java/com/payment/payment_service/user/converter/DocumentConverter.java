package com.payment.payment_service.user.converter;

import com.payment.payment_service.shared.crypto.AesEncryptor;
import com.payment.payment_service.user.value_object.Document;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocumentConverter implements AttributeConverter<Document, String> {

    private final AesEncryptor encryptor;

    public DocumentConverter(AesEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(Document document) {
        return document == null ? null : encryptor.encrypt(document.value());
    }

    @Override
    public Document convertToEntityAttribute(String value) {
        return value == null ? null : new Document(encryptor.decrypt(value));
    }


    
}