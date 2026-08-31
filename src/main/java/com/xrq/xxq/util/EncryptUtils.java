package com.xrq.xxq.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * 加密工具类，提供 PBKDF2 加盐密码哈希
 *
 * @类名 EncryptUtils
 * @Date 2026/6/5
 */
public class EncryptUtils {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";    //PBKDF2算法
    private static final int SALT_LENGTH = 16;                 // 盐长度（字节）
    private static final int HASH_ITERATIONS = 100_000;        // PBKDF2 迭代次数
    private static final int DERIVED_KEY_LENGTH = 256;         // 派生密钥长度（位）

    // ==================== 盐相关方法 ====================

    /**
     * 生成随机盐（Base64 编码）
     *
     * @return 16 字节随机盐的 Base64 字符串
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    // ==================== PBKDF2 密钥派生（加盐） ====================

    /**
     * 使用 PBKDF2 + 盐 派生密钥（用于密码哈希存储）
     *
     * @param plainText 明文（如密码）
     * @param salt      Base64 编码的盐
     * @return "迭代次数:盐:哈希" 格式的字符串，可直接存储到数据库
     */
    public static String hashWithPbkdf2(String plainText, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(
                    plainText.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    HASH_ITERATIONS,
                    DERIVED_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HASH_ITERATIONS + ":" + salt + ":" + bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 哈希失败", e);
        }
    }

    /**
     * 使用 PBKDF2 + 自动生成盐，一步得到存储格式
     *
     * @param plainText 明文
     * @return "迭代次数:盐:哈希" 格式的字符串
     */
    public static String hashWithPbkdf2(String plainText) {
        return hashWithPbkdf2(plainText, generateSalt());
    }

    /**
     * 验证明文是否匹配 PBKDF2 存储格式
     *
     * @param plainText 待验证的明文
     * @param stored    hashWithPbkdf2 生成的存储字符串（"迭代次数:盐:哈希"）
     * @return true 匹配
     */
    public static Boolean verifyPbkdf2(String plainText, String stored) {
        try {
            String[] parts = stored.split(":");
            Integer iterations = Integer.parseInt(parts[0]);
            String salt = parts[1];
            String expectedHash = parts[2];

            KeySpec spec = new PBEKeySpec(
                    plainText.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    iterations,
                    DERIVED_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(bytesToHex(hash).getBytes(), expectedHash.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 验证失败", e);
        }
    }

    // ==================== 内部工具方法 ====================

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (Byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
