package com.payment.payment_service.shared.query;

import java.util.UUID;
import com.payment.payment_service.shared.dto.UserSummary;

public interface UserQueryService {
    UserSummary getSummary(UUID id);
}