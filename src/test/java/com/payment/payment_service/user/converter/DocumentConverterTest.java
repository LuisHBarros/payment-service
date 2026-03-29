package com.payment.payment_service.user.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.payment.payment_service.shared.crypto.AesEncryptor;
import com.payment.payment_service.user.value_object.Document;

class DocumentConverterTest {

    private static final String TEST_KEY = "12345678901234567890123456789012"; // 32 bytes
    private DocumentConverter converter;

    @BeforeEach
    void setUp() {
        AesEncryptor encryptor = new AesEncryptor(TEST_KEY);
        converter = new DocumentConverter(encryptor);
    }

    @Test
    @DisplayName("should encrypt and decrypt CPF document successfully")
    void shouldEncryptAndDecrypt_cpfDocumentSuccessfully() {
        Document cpf = new Document("123.456.789-09");

        String encrypted = converter.convertToDatabaseColumn(cpf);
        Document decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotNull().isNotEqualTo("123.456.789-09");
        assertThat(decrypted).isNotNull();
        assertThat(decrypted.value()).isEqualTo("12345678909");
        assertThat(decrypted.isCpf()).isTrue();
    }

    @Test
    @DisplayName("should encrypt and decrypt CNPJ document successfully")
    void shouldEncryptAndDecrypt_cnpjDocumentSuccessfully() {
        Document cnpj = new Document("12.345.678/0001-95");

        String encrypted = converter.convertToDatabaseColumn(cnpj);
        Document decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotNull().isNotEqualTo("12.345.678/0001-95");
        assertThat(decrypted).isNotNull();
        assertThat(decrypted.value()).isEqualTo("12345678000195");
        assertThat(decrypted.isCnpj()).isTrue();
    }

    @Test
    @DisplayName("should handle null values")
    void shouldHandleNullValues() {
        String encrypted = converter.convertToDatabaseColumn(null);
        Document decrypted = converter.convertToEntityAttribute(null);

        assertThat(encrypted).isNull();
        assertThat(decrypted).isNull();
    }

    @Test
    @DisplayName("should produce different ciphertext for same document due to random IV")
    void shouldProduceDifferentCiphertext_forSameDocument() {
        Document cpf = new Document("123.456.789-09");

        String encrypted1 = converter.convertToDatabaseColumn(cpf);
        String encrypted2 = converter.convertToDatabaseColumn(cpf);

        assertThat(encrypted1).isNotEqualTo(encrypted2);

        Document decrypted1 = converter.convertToEntityAttribute(encrypted1);
        Document decrypted2 = converter.convertToEntityAttribute(encrypted2);

        assertThat(decrypted1.value()).isEqualTo(decrypted2.value());
    }

    @Test
    @DisplayName("should handle document with various formats")
    void shouldHandleDocument_withVariousFormats() {
        String[] cpfFormats = {
            "123.456.789-09",
            "12345678909",
            "123 456 789 09"
        };

        for (String format : cpfFormats) {
            Document document = new Document(format);
            String encrypted = converter.convertToDatabaseColumn(document);
            Document decrypted = converter.convertToEntityAttribute(encrypted);

            assertThat(decrypted.value()).isEqualTo("12345678909");
            assertThat(decrypted.isCpf()).isTrue();
        }
    }

    @Test
    @DisplayName("should preserve document type during encryption/decryption")
    void shouldPreserveDocumentType_duringEncryptionDecryption() {
        Document cpf = new Document("123.456.789-09");
        Document cnpj = new Document("12.345.678/0001-95");

        Document decryptedCpf = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(cpf));
        Document decryptedCnpj = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(cnpj));

        assertThat(decryptedCpf.isCpf()).isTrue();
        assertThat(decryptedCnpj.isCnpj()).isTrue();
    }

    @Test
    @DisplayName("should throw exception for tampered ciphertext due to GCM authentication")
    void shouldThrowException_forTamperedCiphertextDueToGcmAuthentication() {
        Document cpf = new Document("123.456.789-09");
        String encrypted = converter.convertToDatabaseColumn(cpf);

        // Tamper with the ciphertext
        byte[] encryptedBytes = java.util.Base64.getDecoder().decode(encrypted);
        encryptedBytes[0] ^= 0x01; // Flip a bit
        String tampered = java.util.Base64.getEncoder().encodeToString(encryptedBytes);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("decryption failed");
    }
}