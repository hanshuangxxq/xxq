package com.xrq.xxq.module.user.service.avatar;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg");

    @Value("${avatar.storage-path:uploads/avatars}")
    private String avatarStoragePath;

    @Value("${avatar.max-file-size:5242880}")
    private Long maxFileSize;

    private final UserMapper userMapper;

    // ---- 文件存储 ----

    public String saveAvatar(Long userId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件过大，最大允许 " + maxFileSize / 1024 / 1024 + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的图片格式: " + ext);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        Path dir = Path.of(avatarStoragePath);
        Files.createDirectories(dir);

        // 删除旧头像
        if (user.getAvatar() != null && !user.getAvatar().isBlank()) {
            Files.deleteIfExists(dir.resolve(user.getAvatar()));
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(dir.resolve(filename));

        user.setAvatar(filename);
        userMapper.updateById(user);

        return filename;
    }

    // ---- 文件读取与流式返回 ----

    public Path resolveAvatarFile(String filename) {
        Path base = Path.of(avatarStoragePath).normalize();
        Path filePath = base.resolve(filename).normalize();
        if (!filePath.startsWith(base)) {
            throw new IllegalArgumentException("非法的文件路径");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "文件不存在");
        }
        return filePath;
    }

    // ---- Base64（WebSocket 用） ----

    public String getAvatarBase64(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getAvatar() == null || user.getAvatar().isBlank()) {
            return null;
        }

        try {
            Path filePath = Path.of(avatarStoragePath, user.getAvatar());
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException | InvalidPathException | SecurityException e) {
            return null;
        }
    }

    public String getContentType(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getAvatar() == null) {
            return null;
        }
        return resolveContentType(user.getAvatar());
    }

    public String resolveContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        Integer i = filename.lastIndexOf('.');
        return i >= 0 ? filename.substring(i) : "";
    }
}
