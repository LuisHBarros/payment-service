package com.payment.payment_service.user.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.config.SecurityUtils;
import com.payment.payment_service.config.openapi.ApiErrorResponse;
import com.payment.payment_service.user.dto.CreateUserRequestDTO;
import com.payment.payment_service.user.dto.PatchUserRequestDTO;
import com.payment.payment_service.user.dto.UserResponseDTO;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.service.CreateUserService;
import com.payment.payment_service.user.service.DeleteUserService;
import com.payment.payment_service.user.service.GetUserService;
import com.payment.payment_service.user.service.PatchUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User lifecycle endpoints")
public class UserController {

    private final CreateUserService createUserService;
    private final GetUserService getUserService;
    private final DeleteUserService deleteUserService;
    private final PatchUserService patchUserService;

    public UserController(
            CreateUserService createUserService,
            GetUserService getUserService,
            DeleteUserService deleteUserService,
            PatchUserService patchUserService) {
        this.createUserService = createUserService;
        this.getUserService = getUserService;
        this.deleteUserService = deleteUserService;
        this.patchUserService = patchUserService;
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Creates a new user and returns the generated identifier.")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Email or document already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Password or business validation failed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    public ResponseEntity<UUID> create(@RequestBody @Valid CreateUserRequestDTO request) {
        UUID userId = createUserService.execute(
            request.name(),
            request.email(),
            request.password(),
            request.document()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(userId);
    }

    @GetMapping
    @Operation(summary = "List users", description = "Returns a paginated list of users. Requires ADMIN role.")
    public ResponseEntity<Page<UserResponseDTO>> findAll(@PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        Page<UserEntity> users = getUserService.findAll(pageable);
        Page<UserResponseDTO> response = users.map(this::toResponseDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id", description = "Returns a user when the caller is the owner or an admin.")
    public ResponseEntity<UserResponseDTO> findById(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        SecurityUtils.requireOwnership(auth, id);
        UserEntity user = getUserService.findById(id);
        return ResponseEntity.ok(toResponseDTO(user));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Patch user", description = "Updates email and/or password for the target user.")
    public ResponseEntity<UserResponseDTO> patch(
            @PathVariable UUID id,
            @RequestBody PatchUserRequestDTO request,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        SecurityUtils.requireOwnership(auth, id);
        UserEntity user = patchUserService.execute(id, request.email(), request.password());
        return ResponseEntity.ok(toResponseDTO(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Deletes the target user.")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        SecurityUtils.requireOwnership(auth, id);
        deleteUserService.execute(id);
        return ResponseEntity.noContent().build();
    }

    private UserResponseDTO toResponseDTO(UserEntity user) {
        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail().value(),
            user.getDocument().masked(),
            user.getType(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
