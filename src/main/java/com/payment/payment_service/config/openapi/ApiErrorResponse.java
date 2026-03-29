package com.payment.payment_service.config.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiErrorResponse", description = "Standard API error payload")
public record ApiErrorResponse(
    @Schema(description = "HTTP status code", example = "400")
    int status,
    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    String error,
    @Schema(description = "Human-readable error message", example = "identifier: must not be blank")
    String message
) {
}
