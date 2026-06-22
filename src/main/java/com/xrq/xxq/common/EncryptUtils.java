package com.xrq.xxq.common;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * 加密工具类，提供基于盐的哈希和对称加密方法
 *
 * @类名 EncryptUtils
 * @Date 2026/6/5
 */
public class EncryptUtils {

    private static final String HASH_ALGORITHM = "SHA-256";    //哈希加密类型
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";    //PBKDF2算法
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";    //AES算法
    private static final String AES_KEY_ALGORITHM = "AES";     //AES密钥算法
    private static final int SALT_LENGTH = 16;                 // 盐长度（字节）
    private static final int HASH_ITERATIONS = 100_000;        // PBKDF2 迭代次数
    private static final int AES_KEY_LENGTH = 256;             // AES 密钥长度
    private static final int GCM_IV_LENGTH = 12;               // GCM IV 长度（字节）
    private static final int GCM_TAG_LENGTH = 128;             // GCM 认证标签长度（位）

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

    /**
     * 生成指定长度的随机盐（Base64 编码）
     *
     * @param length 盐的字节长度
     * @return Base64 编码的盐
     */
    public static String generateSalt(int length) {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[length];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    // ==================== SHA-256 哈希（加盐） ====================

    /**
     * 使用 SHA-256 + 盐 对明文进行哈希
     *
     * @param plainText 明文
     * @param salt      Base64 编码的盐
     * @return 哈希结果的十六进制字符串
     */
    public static String hashWithSalt(String plainText, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hash = digest.digest(plainText.getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("哈希算法不可用: " + HASH_ALGORITHM, e);
        }
    }

    /**
     * 验证明文是否与加盐哈希值匹配
     *
     * @param plainText  待验证的明文
     * @param salt       Base64 编码的盐
     * @param hashResult 预期的哈希值（十六进制字符串）
     * @return true 匹配，false 不匹配
     */
    public static boolean verifyHash(String plainText, String salt, String hashResult) {
        String computed = hashWithSalt(plainText, salt);
        return MessageDigest.isEqual(computed.getBytes(), hashResult.getBytes());
    }

    // ==================== PBKDF2 密钥派生（加盐） ====================

    /**
     * 使用 PBKDF2 + 盐 派生密钥（用于密码哈希存储，推荐使用此方法而非纯 SHA-256）
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
                    AES_KEY_LENGTH
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
    public static boolean verifyPbkdf2(String plainText, String stored) {
        try {
            String[] parts = stored.split(":");
            int iterations = Integer.parseInt(parts[0]);
            String salt = parts[1];
            String expectedHash = parts[2];

            KeySpec spec = new PBEKeySpec(
                    plainText.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    iterations,
                    AES_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(bytesToHex(hash).getBytes(), expectedHash.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 验证失败", e);
        }
    }

    // ==================== AES-GCM 对称加密（基于盐+密码派生密钥） ====================

    /**
     * 使用密码 + 盐派生 AES 密钥并加密
     *
     * @param plainText 明文
     * @param password  用于派生密钥的密码
     * @param salt      Base64 编码的盐
     * @return "盐:IV:密文" 格式的 Base64 字符串
     */
    public static String encrypt(String plainText, String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);

            // PBKDF2 派生 AES 密钥
            KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, HASH_ITERATIONS, AES_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey key = new SecretKeySpec(tmp.getEncoded(), AES_KEY_ALGORITHM);

            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            byte[] ciphertext = cipher.doFinal(plainText.getBytes());

            // 组合: salt + iv + ciphertext
            byte[] combined = new byte[saltBytes.length + iv.length + ciphertext.length];
            System.arraycopy(saltBytes, 0, combined, 0, saltBytes.length);
            System.arraycopy(iv, 0, combined, saltBytes.length, iv.length);
            System.arraycopy(ciphertext, 0, combined, saltBytes.length + iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * 使用密码 + 自动生成盐的加密（简化版）
     *
     * @param plainText 明文
     * @param password  用于派生密钥的密码
     * @return Base64 编码的 "盐+IV+密文"
     */
    public static String encrypt(String plainText, String password) {
        return encrypt(plainText, password, generateSalt());
    }

    /**
     * 解密 encrypt() 方法产生的密文
     *
     * @param combinedBase64 encrypt() 返回的 Base64 字符串
     * @param password       派生密钥时使用的密码
     * @return 原始明文
     */
    public static String decrypt(String combinedBase64, String password) {
        try {
            byte[] combined = Base64.getDecoder().decode(combinedBase64);
            byte[] saltBytes = new byte[SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - SALT_LENGTH - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, saltBytes, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // 使用相同的 PBKDF2 参数派生密钥
            KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, HASH_ITERATIONS, AES_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey key = new SecretKeySpec(tmp.getEncoded(), AES_KEY_ALGORITHM);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    // ==================== 内部工具方法 ====================

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}