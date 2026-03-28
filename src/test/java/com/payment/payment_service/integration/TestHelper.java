package com.payment.payment_service.integration;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.payment.payment_service.auth.service.JwtService;
import com.payment.payment_service.shared.crypto.HashUtil;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TestHelper {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public UUID createCommonUser(String name, String email, String password) {
        Document doc = new Document(generateValidCpf());
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(new Email(email));
        user.setPassword(passwordEncoder.encode(password));
        user.setType(UserType.COMMON);
        user.setActive(true);
        user.setDocument(doc);
        user.setDocumentHash(HashUtil.sha256(doc.value()));
        return userRepository.save(user).getId();
    }

    public UUID createMerchantUser(String name, String email, String password) {
        Document doc = new Document(generateValidCnpj());
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(new Email(email));
        user.setPassword(passwordEncoder.encode(password));
        user.setType(UserType.MERCHANT);
        user.setActive(true);
        user.setDocument(doc);
        user.setDocumentHash(HashUtil.sha256(doc.value()));
        return userRepository.save(user).getId();
    }

    public UUID createWallet(UUID userId, BigDecimal balance) {
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setBalance(balance);
        return walletRepository.save(wallet).getId();
    }

    public String generateToken(UUID userId, String email, UserType type) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(new Email(email));
        user.setType(type);
        return jwtService.generateToken(user);
    }

    public HttpHeaders authHeaders(UUID userId, String email, UserType type) {
        String token = generateToken(userId, email, type);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(Objects.requireNonNull(token));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    static String generateValidCpf() {
        Random random = new Random();
        int[] digits = new int[9];
        for (int i = 0; i < 9; i++) {
            digits[i] = random.nextInt(10);
        }
        if (digits[0] == 0) {
            digits[0] = 1;
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += digits[i] * (10 - i);
        }
        int check1 = (sum * 10 % 11) % 10;

        sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += digits[i] * (11 - i);
        }
        sum += check1 * 2;
        int check2 = (sum * 10 % 11) % 10;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            sb.append(digits[i]);
        }
        sb.append(check1).append(check2);
        return sb.toString();
    }

    static String generateValidCnpj() {
        Random random = new Random();
        int[] digits = new int[12];
        for (int i = 0; i < 12; i++) {
            digits[i] = random.nextInt(10);
        }
        if (digits[0] == 0) {
            digits[0] = 1;
        }

        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += digits[i] * weights1[i];
        }
        int check1 = (sum % 11 < 2) ? 0 : 11 - (sum % 11);

        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += digits[i] * weights2[i];
        }
        sum += check1 * weights2[12];
        int check2 = (sum % 11 < 2) ? 0 : 11 - (sum % 11);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(digits[i]);
        }
        sb.append(check1).append(check2);
        return sb.toString();
    }
}
