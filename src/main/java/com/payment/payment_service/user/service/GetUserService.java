package com.payment.payment_service.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;

@Service
public class GetUserService {

    private final UserRepository userRepository;

    public GetUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public List<UserEntity> findAll() {
        return userRepository.findAll();
    }
}