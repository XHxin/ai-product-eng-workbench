package com.aiworkbench.backend.integration;

import com.aiworkbench.backend.service.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretEncryptionService {
    private static final String PREFIX = "AESGCM:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(@Value("${WORKBENCH_ENCRYPTION_KEY:${workbench.integrations.encryption-key:}}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new BusinessException("ENCRYPTION_KEY_MISSING", "未配置加密密钥", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new BusinessException("ENCRYPTION_KEY_INVALID", "加密密钥必须是32字节Base64", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        this.secretKey = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (java.security.GeneralSecurityException exception) {
            throw new BusinessException("ENCRYPTION_FAILED", "敏感配置加密失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return "";
        }
        if (!encryptedText.startsWith(PREFIX)) {
            throw new BusinessException("ENCRYPTION_FORMAT_INVALID", "敏感配置格式无效", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] ciphertext = java.util.Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException("DECRYPTION_FAILED", "敏感配置解密失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
