package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.graduation.dto.CampaignMaterialResponse;

/**
 * 毕设活动资料（教务下发的模板/规范等附件，一个活动多份）。
 */
public interface GraduationCampaignMaterialService {

    /** 教务上传资料（file 整传 与 filePath 分片产物 二选一，必填其一）；上传动作记操作日志 */
    CampaignMaterialResponse upload(Long academicUserId, Long campaignId, String filePath,
                                    String fileOriginal, MultipartFile file);

    /** 活动资料列表（可见性与活动详情一致：四种角色） */
    List<CampaignMaterialResponse> list(Long campaignId);

    /** 教务删除资料（逻辑删行 + 释放文件引用）；删除动作记操作日志 */
    void delete(Long academicUserId, Long campaignId, Long materialId);

    /** 资料文件下载解析（可见性与列表一致） */
    FileView resolveMaterialFile(Long campaignId, Long materialId);
}
