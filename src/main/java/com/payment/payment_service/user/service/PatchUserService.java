package com.payment.payment_service.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatchUserService {

    private final GetUserService getUserService;
    private final UpdateUserEmailService updateUserEmailService;
    private final UpdatePasswordService updatePasswordService;

    @Transactional
    public UserEntity execute(UUID id, String email, String password) {
        if (email != null) updateUserEmailService.execute(id, email);
        if (password != null) updatePasswordService.execute(id, password);
        return getUserService.findById(id);
    }
}