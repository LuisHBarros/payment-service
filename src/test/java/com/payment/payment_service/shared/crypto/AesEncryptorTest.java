package com.payment.payment_service.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesEncryptorTest {

    private static final String KEY_16_BYTES = "1234567890123456";
    private static final String KEY_32_BYTES = "12345678901234567890123456789012";

    private AesEncryptor encryptor16;
    private AesEncryptor encryptor32;

    @BeforeEach
    void setUp() {
        encryptor16 = new AesEncryptor(KEY_16_BYTES);
        encryptor32 = new AesEncryptor(KEY_32_BYTES);
    }

    @Test
    @DisplayName("should encrypt and decrypt successfully with 16-byte key")
    void shouldEncryptAndDecrypt_successfully_with16ByteKey() {
        String plainText = "Hello, World!";

        String encrypted = encryptor16.encrypt(plainText);
        String decrypted = encryptor16.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("should encrypt and decrypt successfully with 32-byte key")
    void shouldEncryptAndDecrypt_successfully_with32ByteKey() {
        String plainText = "Sensitive financial data: CPF 123.456.789-00";

        String encrypted = encryptor32.encrypt(plainText);
        String decrypted = encryptor32.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("should handle unicode characters correctly")
    void shouldHandleUnicode_correctly() {
        String plainText = "João Silva - CPF: 123.456.789-00 - 📄 Document";

        String encrypted = encryptor32.encrypt(plainText);
        String decrypted = encryptor32.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("should produce different ciphertext for same plaintext due to random IV")
    void shouldProduceDifferentCiphertext_forSamePlaintext() {
        String plainText = "Test data";

        String encrypted1 = encryptor32.encrypt(plainText);
        String encrypted2 = encryptor32.encrypt(plainText);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptor32.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(encryptor32.decrypt(encrypted2)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("should handle empty string")
    void shouldHandleEmptyString() {
        String plainText = "";

        String encrypted = encryptor32.encrypt(plainText);
        String decrypted = encryptor32.decrypt(encrypted);

        assertThat(decrypted).isEmpty();
    }

    @Test
    @DisplayName("should throw exception for invalid key length")
    void shouldThrowException_forInvalidKeyLength() {
        String invalidKey = "12345678"; // 8 bytes

        assertThatThrownBy(() -> new AesEncryptor(invalidKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("crypto key must be 16 or 32 bytes");
    }

    @Test
    @DisplayName("should detect tampered ciphertext due to GCM authentication")
    void shouldDetectTamperedCiphertext_dueToGcmAuthentication() {
        String plainText = "Sensitive document data";
        String encrypted = encryptor32.encrypt(plainText);

        // Tamper with the ciphertext
        byte[] encryptedBytes = java.util.Base64.getDecoder().decode(encrypted);
        encryptedBytes[0] ^= 0x01; // Flip a bit
        String tampered = java.util.Base64.getEncoder().encodeToString(encryptedBytes);

        assertThatThrownBy(() -> encryptor32.decrypt(tampered))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("decryption failed");
    }

    @Test
    @DisplayName("should handle document-like data formats")
    void shouldHandleDocumentLikeDataFormats() {
        String plainText = "123.456.789-00"; // CPF format

        String encrypted = encryptor32.encrypt(plainText);
        String decrypted = encryptor32.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }
}