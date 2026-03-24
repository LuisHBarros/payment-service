package com.payment.payment_service.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class DeleteUserService {
    
    private final UserRepository userRepository;
    
    public DeleteUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Transactional
    public void execute(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }
    
}
