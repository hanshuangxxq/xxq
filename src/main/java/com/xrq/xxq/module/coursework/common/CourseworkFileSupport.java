package com.xrq.xxq.module.coursework.common;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;

import lombok.RequiredArgsConstructor;

/**
 * coursework 与文件模块的适配层（参考 PracticeFileSupport，但无 legacy 包袱）：
 * 只做「二选一入口」——multipart 整传 或 分片产物路径；跨 biz 目录由 {@code fileStorage.bind} 拒绝。
 * 不提供下载解析（统一走 file 模块 POST 通用下载）与删除（内容寻址产物由 FileMaintenanceTask 回收）。
 */
@Component
@RequiredArgsConstructor
public class CourseworkFileSupport {

    private final FileStorageService fileStorage;

    /**
     * @param filePath 分片产物路径（objects/...），与 file 二选一
     * @param file     multipart 整传文件，与 filePath 二选一
     * @param biz      业务目录（决定存储子目录与扩展名白名单）
     * @param required 是否必填
     * @return 产物引用；非必填且两者都没给时返回 null
     */
    public StoredFileRef resolveSubmit(String filePath, MultipartFile file, FileBizEnum biz, boolean required) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasPath = filePath != null && !filePath.isBlank();
        if (hasFile && hasPath) {
            throw new BusinessException(400, "file 与 filePath 只能二选一");
        }
        if (hasFile) {
            return fileStorage.storeWhole(biz, file);
        }
        if (hasPath) {
            return fileStorage.bind(biz, filePath.trim());
        }
        if (required) {
            throw new BusinessException(400, "文件不能为空");
        }
        return null;
    }
}
