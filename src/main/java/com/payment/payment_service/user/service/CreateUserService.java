package com.payment.payment_service.user.service;

import java.util.UUID;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exception.UserDocumentException;
import com.payment.payment_service.user.exception.UserEmailException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Email;
import com.payment.payment_service.user.value_object.Password;
import com.payment.payment_service.user.value_object.Document;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.crypto.HashUtil;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.metrics.PaymentMetrics;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.shared.event.UserCreatedEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PaymentMetrics metrics;

    @Transactional
    public UUID execute(String name, String email, String password, String document) {
        final var emailValue = new Email(email);
        final var passwordValue = new Password(password);
        final var doc = new Document(document);
        final var documentHash = HashUtil.sha256(doc.value());

        if (userRepository.existsByEmail(emailValue)) {
            throw new UserEmailException("email already registered");
        }

        if (userRepository.existsByDocumentHash(documentHash)) {
            throw new UserDocumentException("document already registered");
        }

        final UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(emailValue);
        user.setPassword(passwordEncoder.encode(passwordValue.value()));
        user.setType(UserType.from(doc));
        user.setDocument(doc);
        user.setDocumentHash(documentHash);

        userRepository.save(user);
        saveOutbox(user.getId());
        metrics.recordUserCreated(user.getType().name());
        return user.getId();
    }

    private void saveOutbox(UUID userId) {
        try {
            var event = new UserCreatedEvent(userId);
            var outbox = new OutboxEntity();
            outbox.setAggregateId(userId);
            outbox.setAggregateType("user");
            outbox.setEventType("USER_CREATED");
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize user created event", e);
        }
    }
}
