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

import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.metrics.PaymentMetrics;
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
    private KafkaEventProducer kafkaEventProducer;

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
        reset(userRepository, passwordEncoder, kafkaEventProducer, metrics);
    }

    @Test
    void execute_WithValidData_ShouldCreateUserAndReturnId() {
        // Arrange
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByDocumentHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        // Act
        UUID userId = createUserService.execute(validName, validEmail, validPassword, validDocument);

        // Assert
        assertNotNull(userId);
        verify(userRepository).save(any(UserEntity.class));
        verify(passwordEncoder).encode(anyString());
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
}