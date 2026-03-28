package com.payment.payment_service.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.user.entity.UserEntity;

import org.springframework.transaction.annotation.Transactional;

@Service
public class PatchUserService {

    private final GetUserService getUserService;
    private final UpdateUserEmailService updateUserEmailService;
    private final UpdatePasswordService updatePasswordService;

    public PatchUserService(
            GetUserService getUserService,
            UpdateUserEmailService updateUserEmailService,
            UpdatePasswordService updatePasswordService) {
        this.getUserService = getUserService;
        this.updateUserEmailService = updateUserEmailService;
        this.updatePasswordService = updatePasswordService;
    }

    @Transactional
    public UserEntity execute(UUID id, String email, String password) {
        if (email != null) updateUserEmailService.execute(id, email);
        if (password != null) updatePasswordService.execute(id, password);
        return getUserService.findById(id);
    }
}