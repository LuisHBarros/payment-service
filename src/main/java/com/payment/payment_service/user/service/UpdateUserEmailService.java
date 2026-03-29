package com.payment.payment_service.user.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.user.value_object.Email;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exception.UserEmailException;
import com.payment.payment_service.user.exception.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UpdateUserEmailService {

    private final UserRepository userRepository;

    @Transactional
    public UserEntity execute(UUID id, String email) {
        UserEntity user = userRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
                
        var newEmail = new Email(email);
        if (user.getEmail() != null && user.getEmail().equals(newEmail)) {
            throw new UserEmailException("Email is the same");
        }

        user.setEmail(newEmail);

        return userRepository.save(user);
    }
}