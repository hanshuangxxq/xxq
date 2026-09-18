package com.xrq.xxq.module.practice.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * practice 各子域与文件模块之间的适配层，取代原先独立的 {@code PracticeFileService}。
 * <p>
 * 承担三件事：
 * <ol>
 *   <li><b>二选一入口</b>：小文件走 multipart 的 {@code file} 部分整传，大文件走分片产物的
 *       {@code filePath} —— 两者同时给是 400（避免「传了分片又传了整文件，到底用哪个」的歧义）。</li>
 *   <li><b>新旧格式双读</b>：{@code objects/...} 前缀走文件模块，其余当作 legacy 扁平目录里的
 *       UUID 文件名。迁移脚本可重入可回滚，回滚窗口内业务表必然两种格式混存 ——
 *       只认新格式会让 5 个下载端点集体 404。</li>
 *   <li><b>删除降级</b>：新格式产品是内容寻址的，同一份可能被多行引用，<b>绝不即时删</b>；
 *       legacy 文件是一次上传一个 UUID、与业务行 1:1，可安全即时删除。</li>
 * </ol>
 * <b>收敛条件</b>：迁移完成 + 观察一个窗口 + 全表 {@code file_name NOT LIKE 'objects/%'} 计数为 0 后，
 * 可删掉 legacy 分支与 {@code practice.storage-path} 配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PracticeFileSupport {

    private static final String NEW_PREFIX = FileStorageService.OBJECTS_PREFIX;

    @Value("${practice.storage-path:uploads/practice}")
    private String legacyStoragePath;

    private final FileStorageService fileStorage;

    /**
     * 二选一入口：解析本次提交携带的文件。
     *
     * @param filePath          分片产物路径（{@code objects/...}），与 {@code file} 二选一
     * @param fileOriginal      展示文件名，配合 {@code filePath} 使用；留空则回退产物文件名
     * @param file              multipart 整传文件，与 {@code filePath} 二选一
     * @param biz               业务目录（决定存储子目录与扩展名白名单）
     * @param required          是否必填（开题/中期为可选附件）
     * @return 产物引用；非必填且两者都没给时返回 {@code null}
     */
    public StoredFileRef resolveSubmit(String filePath, String fileOriginal, MultipartFile file,
                                       FileBizEnum biz, boolean required) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasPath = filePath != null && !filePath.isBlank();
        if (hasFile && hasPath) {
            throw new BusinessException(400, "file 与 filePath 只能二选一");
        }
        if (hasFile) {
            return fileStorage.storeWhole(biz, file);
        }
        if (hasPath) {
            StoredFileRef ref = fileStorage.bind(biz, filePath);
            String displayName = (fileOriginal == null || fileOriginal.isBlank())
                    ? ref.originalName() : fileOriginal.trim();
            return new StoredFileRef(ref.storedPath(), displayName, ref.size(), ref.sha256(), ref.biz());
        }
        if (required) {
            throw new BusinessException(400, "文件不能为空");
        }
        return null;
    }

    /**
     * 解析业务表里存的存储路径为磁盘路径，兼容新旧两种格式。
     * <p>判定用前缀而非「试错」：legacy 目录里不存在以 {@code objects/} 开头的合法文件名，
     * 前缀判断是确定性的。
     */
    public Path resolveForDownload(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new BusinessException(400, "文件名为空");
        }
        if (storedName.startsWith(NEW_PREFIX)) {
            return fileStorage.resolve(storedName);
        }
        Path base = Path.of(legacyStoragePath).normalize();
        Path filePath = base.resolve(storedName).normalize();
        if (!filePath.startsWith(base)) {
            throw new BusinessException(400, "非法的文件路径");
        }
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "文件不存在");
        }
        return filePath;
    }

    /**
     * 释放一个文件引用（记录被删除/被替换时调用）。
     * <p><b>名字是 release 不是 delete，因为对内容寻址产物它不做任何删除</b>：
     * 同一 {@code sha256} 可能被多行业务数据引用（同一份模板论文被两个学生提交），
     * 按路径直接删会误删他人数据。真正的回收由
     * {@code FileMaintenanceTask} 在「无引用 + 宽限期」后执行。
     * <p>legacy 文件例外：一次上传一个 UUID 文件、与业务行 1:1，不存在共享，可安全即时删除
     * （也维持了改造前 {@code PracticeFileService.delete} 的行为，避免迁移窗口内泄漏失控）。
     */
    public void release(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.startsWith(NEW_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveForDownload(storedName));
        } catch (IOException | InvalidPathException | BusinessException e) {
            // 文件已不在或路径非法都不该阻断业务；legacy 残留最终由运维在迁移收敛后整体清理
            log.debug("legacy 文件释放跳过 {}: {}", storedName, e.toString());
        }
    }
}
