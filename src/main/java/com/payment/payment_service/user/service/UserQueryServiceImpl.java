package com.payment.payment_service.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.shared.dto.UserSummary;
import com.payment.payment_service.shared.query.UserQueryService;
import com.payment.payment_service.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final GetUserService getUserService;

    @Override
    public UserSummary getSummary(UUID id) {
        UserEntity user = getUserService.findById(id);
        return new UserSummary(user.getId(), user.getType(), user.isActive());
    }
}