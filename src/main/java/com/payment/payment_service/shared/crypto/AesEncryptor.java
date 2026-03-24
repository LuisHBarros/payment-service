package com.payment.payment_service.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesEncryptor {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private final SecretKeySpec keySpec;

    public AesEncryptor(@Value("${app.crypto.secret}") String secret) {
        byte[] key = secret.getBytes();
        if (key.length != 16 && key.length != 32) {
            throw new IllegalArgumentException("crypto key must be 16 or 32 bytes");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            byte[] combined = new byte[16 + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, 16);
            System.arraycopy(encrypted, 0, combined, 16, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("encryption failed", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new RuntimeException("decryption failed", e);
        }
    }
}