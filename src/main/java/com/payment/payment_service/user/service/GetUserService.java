package com.payment.payment_service.user.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        return userRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public Page<UserEntity> findAll(Pageable pageable) {
        return userRepository.findAll(Objects.requireNonNull(pageable));
    }
}
