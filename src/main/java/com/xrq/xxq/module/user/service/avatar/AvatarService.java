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

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(dir.resolve(filename));

        // 顺序：先落新文件 → 再更新 DB → 最后尽力删旧文件。
        // 旧顺序是「先删旧 → 存新」，一旦 transferTo 失败，DB 仍指向刚被删掉的文件，
        // 用户头像直接变成 404 且无从恢复。
        String previous = user.getAvatar();
        user.setAvatar(filename);
        userMapper.updateById(user);
        deleteOldAvatarQuietly(previous);

        return filename;
    }

    /** 尽力删除被替换掉的旧头像：路径非法/文件已不在都不该阻断头像更新。 */
    private void deleteOldAvatarQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(safeResolve(Path.of(avatarStoragePath), storedName));
        } catch (IOException | IllegalArgumentException ignored) {
            // 旧数据可能含手工塞入的异常值，清理失败不影响业务
        }
    }

    // ---- 文件读取与流式返回 ----

    public Path resolveAvatarFile(String filename) {
        Path filePath = safeResolve(Path.of(avatarStoragePath), filename);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "文件不存在");
        }
        return filePath;
    }

    /**
     * 在存储根目录下安全解析相对文件名：{@code normalize} + {@code startsWith} 路径穿越防护。
     * <p>只做路径解析，<b>不检查存在性</b> —— 调用方用途不同：下载要 404，删除旧文件则不能因
     * 文件已不存在而失败。所有接触存储名的代码路径都必须经过本方法
     * （历史上 {@code getAvatarBase64} 与删除旧头像两处绕过了校验，直接拼接路径）。
     * <p>包级可见以便单测直接覆盖（无需构造 Mapper）。
     */
    static Path safeResolve(Path baseDir, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("文件名为空");
        }
        Path base = baseDir.normalize();
        Path resolved = base.resolve(name).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("非法的文件路径");
        }
        return resolved;
    }

    // ---- Base64（WebSocket 用） ----

    public String getAvatarBase64(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getAvatar() == null || user.getAvatar().isBlank()) {
            return null;
        }

        try {
            // 必须走 safeResolve：此处历史上是 Path.of(root, avatar) 直接拼接，
            // 缺了穿越校验，DB 里若存在 "../../x" 形态的值即可读到存储根之外
            Path filePath = safeResolve(Path.of(avatarStoragePath), user.getAvatar());
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            // IllegalArgumentException 覆盖 InvalidPathException（后者是其子类，不能并列捕获）
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
