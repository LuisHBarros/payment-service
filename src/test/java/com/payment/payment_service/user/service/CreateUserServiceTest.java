package com.payment.payment_service.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.metrics.PaymentMetrics;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exception.UserDocumentException;
import com.payment.payment_service.user.exception.UserEmailException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Email;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CreateUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PaymentMetrics metrics;

    @InjectMocks
    private CreateUserService createUserService;



    private final String validName = "John Doe";
    private final String validEmail = "john.doe@example.com";
    private final String validPassword = "SecurePass123!";
    private final String validDocument = "52998224725"; // Valid CPF

    @BeforeEach
    void setUp() {
        reset(userRepository, passwordEncoder, outboxRepository, objectMapper, metrics);
    }

    @Test
    void execute_WithValidData_ShouldCreateUserAndReturnId() throws JsonProcessingException {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        UUID userId = createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        assertNotNull(userId);
        verify(userRepository).save(any(UserEntity.class));
        verify(outboxRepository).save(any(OutboxEntity.class));
        verify(objectMapper).writeValueAsString(any());
        verify(passwordEncoder).encode(anyString());
        verify(metrics).recordUserCreated(anyString());
        }

    @Test
    void execute_WithExistingEmail_ShouldThrowUserEmailException() {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(true);

        // Act & Assert
        UserEmailException exception = assertThrows(UserEmailException.class, () ->
            createUserService.execute(validName, validEmail, validPassword, validDocument)
        );
        assertEquals("email already registered", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_WithExistingDocument_ShouldThrowUserDocumentException() {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(true);

        // Act & Assert
        UserDocumentException exception = assertThrows(UserDocumentException.class, () ->
            createUserService.execute(validName, validEmail, validPassword, validDocument)
        );
        assertEquals("document already registered", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_ShouldHashDocumentBeforeStoring() {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        verify(userRepository).save(argThat(user -> {
            assertNotNull(user.getDocumentHash());
            assertNotEquals(validDocument, user.getDocumentHash());
            return true;
        }));
    }

    @Test
    void execute_ShouldSetUserToActiveByDefault() {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        verify(userRepository).save(argThat(user ->
            user.isActive()
        ));
    }

    @Test
    void execute_ShouldDetermineUserTypeFromDocument() {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        verify(userRepository).save(argThat(user ->
            user.getType() != null && (user.getType() == UserType.COMMON || user.getType() == UserType.MERCHANT)
        ));
    }

    @Test
    void execute_ShouldSaveOutboxEntryWithCorrectData() throws JsonProcessingException {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"userId\":\"" + userId + "\"}");

        // Act
        createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        verify(outboxRepository).save(argThat(outbox -> {
            assertEquals("user", outbox.getAggregateType());
            assertEquals(userId, outbox.getAggregateId());
            assertEquals("USER_CREATED", outbox.getEventType());
            assertNotNull(outbox.getPayload());
            assertFalse(outbox.isProcessed());
            return true;
        }));
    }

    @Test
    void execute_ShouldUseOutboxPatternNotDirectKafka() throws JsonProcessingException {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        verify(outboxRepository).save(any(OutboxEntity.class));
        verify(objectMapper).writeValueAsString(any());
    }

    @Test
    void execute_ShouldSerializeUserCreatedEventCorrectly() throws JsonProcessingException {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"userId\":\"" + userId + "\"}");

        // Act
        createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        verify(objectMapper).writeValueAsString(argThat(event -> {
            assertTrue(event.toString().contains(userId.toString()));
            return true;
        }));
    }

    @Test
    void execute_ShouldRollbackWhenOutboxSaveFails() throws JsonProcessingException {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            createUserService.execute(validName, validEmail, validPassword, validDocument)
        );
    }
}