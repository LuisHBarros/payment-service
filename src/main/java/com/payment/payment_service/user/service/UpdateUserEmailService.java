package com.payment.payment_service.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.user.value_object.Email;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exceptions.UserEmailException;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;

@Service
public class UpdateUserEmailService {

    private final UserRepository userRepository;

    public UpdateUserEmailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity execute(UUID id, String email) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
                
        var newEmail = new Email(email);
        if (user.getEmail() != null && user.getEmail().equals(newEmail)) {
            throw new UserEmailException("Email is the same");
        }

        user.setEmail(newEmail);

        return userRepository.save(user);
    }
}