package com.payment.payment_service.user.entity;


import com.payment.payment_service.shared.entity.BaseEntity;
import com.payment.payment_service.user.converter.DocumentConverter;
import com.payment.payment_service.user.converter.EmailConverter;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity extends BaseEntity {
    @Column(nullable = false)
    private String name;

    @Convert(converter = EmailConverter.class)
    @Column(nullable = false, unique = true)
    private Email email;
    
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType type;

    @Column(nullable = false)
    private boolean active = true;

    @Convert(converter = DocumentConverter.class)
    @Column(nullable = false)
    private Document document;

    @Column(name = "document_hash", nullable = false, unique = true)
    private String documentHash;
    
}
