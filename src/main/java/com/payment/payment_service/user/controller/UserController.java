package com.payment.payment_service.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.payment.payment_service.user.dto.CreateUserRequestDTO;
import com.payment.payment_service.user.dto.PatchUserRequestDTO;
import com.payment.payment_service.user.dto.UserResponseDTO;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.service.CreateUserService;
import com.payment.payment_service.user.service.DeleteUserService;
import com.payment.payment_service.user.service.GetUserService;
import com.payment.payment_service.user.service.PatchUserService;

@RestController
@RequestMapping("/api/v1/users")
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
    public ResponseEntity<UUID> create(@RequestBody CreateUserRequestDTO request) {
        UUID userId = createUserService.execute(
            request.name(),
            request.email(),
            request.password(),
            request.document()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(userId);
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> findAll() {
        List<UserEntity> users = getUserService.findAll();
        List<UserResponseDTO> response = users.stream()
            .map(this::toResponseDTO)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> findById(@PathVariable UUID id) {
        UserEntity user = getUserService.findById(id);
        return ResponseEntity.ok(toResponseDTO(user));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponseDTO> patch(
            @PathVariable UUID id,
            @RequestBody PatchUserRequestDTO request) {
        UserEntity user = patchUserService.execute(id, request.email(), request.password());
        return ResponseEntity.ok(toResponseDTO(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
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
            user.getActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
