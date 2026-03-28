package com.payment.payment_service.user.repository;

import com.payment.payment_service.user.entity.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmail(com.payment.payment_service.user.value_object.Email email);
    boolean existsByDocumentHash(String documentHash);
    Optional<UserEntity> findByEmail(com.payment.payment_service.user.value_object.Email email);
    Optional<UserEntity> findByDocumentHash(String documentHash);
}
