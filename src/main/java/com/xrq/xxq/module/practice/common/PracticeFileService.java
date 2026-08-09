package com.xrq.xxq.module.practice.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.BusinessException;

/**
 * 实践与创新模块文件上传服务。
 * <p>
 * 独立于 {@code AvatarService}，采用相同的本地存储模式：配置路径 + UUID 文件名 +
 * {@code transferTo} + 路径穿越防护。用于毕业论文、实习报告、社会实践报告等文档提交。
 */
@Service
public class PracticeFileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".doc", ".docx", ".pdf", ".zip", ".rar");

    @Value("${practice.storage-path:uploads/practice}")
    private String storagePath;

    @Value("${practice.max-file-size:20971520}")
    private long maxFileSize;

    /** 存储上传文件，返回存储名与原始文件名。 */
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(400, "文件过大，最大允许 " + maxFileSize / 1024 / 1024 + "MB");
        }
        String original = file.getOriginalFilename();
        String ext = getExtension(original).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "不支持的文件格式: " + ext);
        }
        try {
            Path dir = Path.of(storagePath);
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
            file.transferTo(dir.resolve(storedName));
            return new StoredFile(storedName, original);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败");
        }
    }

    /** 解析存储名为绝对路径，含路径穿越防护。 */
    public Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new BusinessException(400, "文件名为空");
        }
        Path base = Path.of(storagePath).normalize();
        Path filePath = base.resolve(storedName).normalize();
        if (!filePath.startsWith(base)) {
            throw new BusinessException(400, "非法的文件路径");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "文件不存在");
        }
        return filePath;
    }

    /** 尽力删除已存储文件；文件不存在或删除失败均不抛异常，不阻断业务事务。 */
    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Path base = Path.of(storagePath).normalize();
            Path filePath = base.resolve(storedName).normalize();
            if (!filePath.startsWith(base)) {
                return;
            }
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // 文件清理失败不影响业务删除
        }
    }

    /** 根据扩展名推断 Content-Type。 */
    public String contentType(String storedName) {
        if (storedName == null) {
            return "application/octet-stream";
        }
        String lower = storedName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".rar")) return "application/vnd.rar";
        return "application/octet-stream";
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int i = filename.lastIndexOf('.');
        return i >= 0 ? filename.substring(i) : "";
    }

    /** 已存储文件信息：存储名（磁盘文件名）+ 原始文件名（展示用）。 */
    public record StoredFile(String storedName, String originalName) {
    }
}
