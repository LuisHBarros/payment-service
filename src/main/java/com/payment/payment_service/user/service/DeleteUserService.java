package com.payment.payment_service.user.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exception.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteUserService {
    
    private final UserRepository userRepository;
    
    @Transactional
    public void execute(UUID id) {
        UserEntity user = userRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }
    
}
