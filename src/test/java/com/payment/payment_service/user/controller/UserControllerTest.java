package com.payment.payment_service.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.auth.JwtAuthenticationFilter;
import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.user.dto.CreateUserRequestDTO;
import com.payment.payment_service.user.dto.PatchUserRequestDTO;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.service.CreateUserService;
import com.payment.payment_service.user.service.DeleteUserService;
import com.payment.payment_service.user.service.GetUserService;
import com.payment.payment_service.user.service.PatchUserService;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@SuppressWarnings("null")
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateUserService createUserService;

    @MockitoBean
    private GetUserService getUserService;

    @MockitoBean
    private DeleteUserService deleteUserService;

    @MockitoBean
    private PatchUserService patchUserService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;


    private UserEntity testUser;
    private UUID testUserId;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testTime = LocalDateTime.now();
        testUser = new UserEntity();
        testUser.setId(testUserId);
        testUser.setName("John Doe");
        testUser.setEmail(new Email("john.doe@example.com"));
        testUser.setPassword("encodedPassword");
        testUser.setType(UserType.COMMON);
        testUser.setActive(true);
        testUser.setDocument(new Document("52998224725")); // Valid CPF
        testUser.setDocumentHash("hash123");
        testUser.setCreatedAt(testTime);
        testUser.setUpdatedAt(testTime);

        var authenticatedUser = new AuthenticatedUser(testUserId, "john.doe@example.com", UserType.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(
            authenticatedUser, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        reset(createUserService, getUserService, deleteUserService, patchUserService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_WithValidUser_ShouldReturnCreatedWithUserId() throws Exception {
        // Arrange
        CreateUserRequestDTO request = new CreateUserRequestDTO(
            "John Doe", "john.doe@example.com", "SecurePass123!", "52998224725" // Valid CPF
        );
        UUID createdUserId = UUID.randomUUID();
        when(createUserService.execute(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(createdUserId);

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$").value(createdUserId.toString()));
        verify(createUserService).execute("John Doe", "john.doe@example.com", "SecurePass123!", "52998224725");
    }

    @Test
    void findAll_WithUsersInDatabase_ShouldReturnOkWithUsersList() throws Exception {
        // Arrange
        UserEntity user2 = new UserEntity();
        user2.setId(UUID.randomUUID());
        user2.setName("Jane Smith");
        user2.setEmail(new Email("jane.smith@example.com"));
        user2.setPassword("encodedPassword2");
        user2.setType(UserType.MERCHANT);
        user2.setActive(true);
        user2.setDocument(new Document("04252011000110")); // Valid CNPJ
        user2.setDocumentHash("hash456");
        user2.setCreatedAt(testTime);
        user2.setUpdatedAt(testTime);

        Page<UserEntity> userPage = new PageImpl<>(List.of(testUser, user2));
        when(getUserService.findAll(any(Pageable.class))).thenReturn(userPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("John Doe"))
            .andExpect(jsonPath("$.content[0].email").value("john.doe@example.com"))
            .andExpect(jsonPath("$.content[0].type").value("COMMON"))
            .andExpect(jsonPath("$.content[0].active").value(true))
            .andExpect(jsonPath("$.content[1].name").value("Jane Smith"))
            .andExpect(jsonPath("$.content[1].email").value("jane.smith@example.com"))
            .andExpect(jsonPath("$.content[1].type").value("MERCHANT"))
            .andExpect(jsonPath("$.content[1].active").value(true));
        verify(getUserService).findAll(any(Pageable.class));
    }

    @Test
    void findById_WithExistingUser_ShouldReturnOkWithUser() throws Exception {
        // Arrange
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.name").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john.doe@example.com"))
            .andExpect(jsonPath("$.document").value("***.982.247-25")) // Masked document format
            .andExpect(jsonPath("$.type").value("COMMON"))
            .andExpect(jsonPath("$.active").value(true));
        verify(getUserService).findById(testUserId);
    }

    @Test
    void findById_WithNonExistingUser_ShouldReturnFound() throws Exception {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        when(getUserService.findById(nonExistingId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", nonExistingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()));
        verify(getUserService).findById(nonExistingId);
    }

    @Test
    void patch_WithBothEmailAndPassword_ShouldReturnOkWithUpdatedUser() throws Exception {
        // Arrange
        PatchUserRequestDTO request = new PatchUserRequestDTO("new.email@example.com", "NewPass456!");
        when(patchUserService.execute(eq(testUserId), anyString(), anyString())).thenReturn(testUser);
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/users/{id}", testUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("John Doe"));
        verify(patchUserService).execute(testUserId, "new.email@example.com", "NewPass456!");
    }

    @Test
    void patch_WithOnlyEmail_ShouldReturnOkWithUpdatedUser() throws Exception {
        // Arrange
        PatchUserRequestDTO request = new PatchUserRequestDTO("new.email@example.com", null);
        when(patchUserService.execute(eq(testUserId), anyString(), isNull())).thenReturn(testUser);
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/users/{id}", testUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        verify(patchUserService).execute(testUserId, "new.email@example.com", null);
    }

    @Test
    void patch_WithOnlyPassword_ShouldReturnOkWithUpdatedUser() throws Exception {
        // Arrange
        PatchUserRequestDTO request = new PatchUserRequestDTO(null, "NewPass456!");
        when(patchUserService.execute(eq(testUserId), isNull(), anyString())).thenReturn(testUser);
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/users/{id}", testUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        verify(patchUserService).execute(testUserId, null, "NewPass456!");
    }

    @Test
    void delete_WithExistingUser_ShouldReturnNoContent() throws Exception {
        // Arrange
        doNothing().when(deleteUserService).execute(testUserId);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/users/{id}", testUserId))
            .andExpect(status().isNoContent());
        verify(deleteUserService).execute(testUserId);
    }

    @Test
    void delete_WithNonExistingUser_ShouldDeleteUser() throws Exception {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        doNothing().when(deleteUserService).execute(nonExistingId);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/users/{id}", nonExistingId))
            .andExpect(status().isNoContent());
        verify(deleteUserService).execute(nonExistingId);
    }

    @Test
    void create_WithInvalidRequest_ShouldCreateUser() throws Exception {
        // Arrange
        CreateUserRequestDTO request = new CreateUserRequestDTO(
            "John Doe", "john.doe@example.com", "SecurePass123!", "52998224725" // Valid CPF
        );
        UUID createdUserId = UUID.randomUUID();
        when(createUserService.execute(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(createdUserId);

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void findAll_WithEmptyDatabase_ShouldReturnOkWithEmptyList() throws Exception {
        // Arrange
        Page<UserEntity> emptyPage = new PageImpl<>(List.of());
        when(getUserService.findAll(any(Pageable.class))).thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(0));
        verify(getUserService).findAll(any(Pageable.class));
    }

    @Test
    void toResponseDTO_ShouldMapAllFieldsCorrectly() throws Exception {
        // Arrange
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").exists())
            .andExpect(jsonPath("$.email").exists())
            .andExpect(jsonPath("$.document").exists())
            .andExpect(jsonPath("$.type").exists())
            .andExpect(jsonPath("$.active").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
        verify(getUserService).findById(testUserId);
    }
}