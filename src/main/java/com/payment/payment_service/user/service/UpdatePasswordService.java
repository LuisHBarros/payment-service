package com.payment.payment_service.user.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.exceptions.UserPasswordException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.value_object.Password;

import jakarta.transaction.Transactional;

@Service
public class UpdatePasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UpdatePasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserEntity execute(UUID id, String password) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        var newPassword = new Password(password);

        if (user.getPassword() != null && passwordEncoder.matches(newPassword.value(), user.getPassword())) {
            throw new UserPasswordException("New password cannot be the same as the current password");
        }

        var newHashedPassword = passwordEncoder.encode(newPassword.value());
        user.setPassword(newHashedPassword);

        return userRepository.save(user);
    }

}
