package com.gisagent.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 加密存储服务（AES-256-GCM）。
 *
 * <p>master key 取自环境变量 APP_MASTER_KEY；未配置时回退 JWT_SECRET；
 * 仍缺失则使用开发占位密钥并告警（仅本地开发，生产必须配置 APP_MASTER_KEY）。</p>
 *
 * <p>存储格式：base64( ivLen(4) | iv(12) | ciphertext )，解密失败则原样返回（兼容历史明文）。</p>
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;

    public EncryptionService(@Value("${app.master-key:}") String masterKey,
                             @Value("${app.jwt.secret:}") String jwtSecret) {
        String src;
        if (masterKey != null && !masterKey.isBlank()) {
            src = masterKey;
        } else if (jwtSecret != null && !jwtSecret.isBlank()
                   && !jwtSecret.equals("change-me-in-production-use-at-least-256-bits")) {
            src = jwtSecret;
        } else {
            src = "dev-only-insecure-master-key-change-me";
            log.warn("app.master-key 未配置，回退开发占位密钥；生产环境请设置 APP_MASTER_KEY 环境变量");
        }
        try {
            this.key = MessageDigest.getInstance("SHA-256").digest(src.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密密钥失败", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(4 + IV_LEN + ct.length);
            buf.putInt(IV_LEN);
            buf.put(iv);
            buf.put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            log.error("加密失败", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        try {
            byte[] data = Base64.getDecoder().decode(stored);
            ByteBuffer buf = ByteBuffer.wrap(data);
            int ivLen = buf.getInt();
            byte[] iv = new byte[ivLen];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容历史明文存储：解密失败直接返回原值
            return stored;
        }
    }
}
