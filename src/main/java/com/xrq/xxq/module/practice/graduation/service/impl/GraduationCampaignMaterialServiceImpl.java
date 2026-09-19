package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.common.PracticeFileSupport;
import com.xrq.xxq.module.practice.graduation.dto.CampaignMaterialResponse;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaignMaterial;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMaterialMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationCampaignMaterialService;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationCampaignMaterialServiceImpl
        extends ServiceImpl<GraduationCampaignMaterialMapper, GraduationCampaignMaterial>
        implements GraduationCampaignMaterialService {

    private final GraduationCampaignMapper campaignMapper;
    private final PracticeFileSupport fileSupport;
    private final GraduationLogService logService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public CampaignMaterialResponse upload(Long academicUserId, Long campaignId, String filePath,
                                           String fileOriginal, MultipartFile file) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        StoredFileRef ref = fileSupport.resolveSubmit(filePath, fileOriginal, file,
                FileBizEnum.GRADUATION_CAMPAIGN_MATERIAL, true);
        GraduationCampaignMaterial material = new GraduationCampaignMaterial();
        material.setCampaignId(campaign.getId());
        material.setFileName(ref.storedPath());
        material.setFileOriginal(ref.originalName());
        material.setUploaderId(academicUserId);
        material.setCreateTime(LocalDateTime.now());
        save(material);
        logService.record(campaign.getId(), academicUserId, "academic_admin", "上传活动资料",
                "graduation_campaign_material", material.getId(), "文件: " + ref.originalName());
        return toResponse(material, nameOf(academicUserId));
    }

    @Override
    public List<CampaignMaterialResponse> list(Long campaignId) {
        requireCampaign(campaignId);
        List<GraduationCampaignMaterial> list = list(new LambdaQueryWrapper<GraduationCampaignMaterial>()
                .eq(GraduationCampaignMaterial::getCampaignId, campaignId)
                .orderByDesc(GraduationCampaignMaterial::getId));
        List<Long> uploaderIds = list.stream().map(GraduationCampaignMaterial::getUploaderId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = uploaderIds.isEmpty() ? Map.of() : userMapper.toNameMap(uploaderIds);
        return list.stream()
                .map(m -> toResponse(m, m.getUploaderId() == null ? null : names.get(m.getUploaderId())))
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long academicUserId, Long campaignId, Long materialId) {
        GraduationCampaignMaterial material = requireMaterial(campaignId, materialId);
        // @TableLogic 逻辑删除：引用对账即刻失效，物理文件由 FileMaintenanceTask 宽限期后回收
        removeById(materialId);
        fileSupport.release(material.getFileName());
        logService.record(campaignId, academicUserId, "academic_admin", "删除活动资料",
                "graduation_campaign_material", materialId, "文件: " + material.getFileOriginal());
    }

    @Override
    public FileView resolveMaterialFile(Long campaignId, Long materialId) {
        GraduationCampaignMaterial material = requireMaterial(campaignId, materialId);
        return new FileView(fileSupport.resolveForDownload(material.getFileName()),
                material.getFileOriginal());
    }

    // ---- helpers ----

    private GraduationCampaign requireCampaign(Long campaignId) {
        GraduationCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private GraduationCampaignMaterial requireMaterial(Long campaignId, Long materialId) {
        GraduationCampaignMaterial material = getById(materialId);
        if (material == null || !material.getCampaignId().equals(campaignId)) {
            throw new BusinessException(404, "资料不存在");
        }
        return material;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }

    private CampaignMaterialResponse toResponse(GraduationCampaignMaterial material, String uploaderName) {
        CampaignMaterialResponse resp = new CampaignMaterialResponse();
        resp.setId(material.getId());
        resp.setCampaignId(material.getCampaignId());
        resp.setFileOriginal(material.getFileOriginal());
        resp.setUploaderId(material.getUploaderId());
        resp.setUploaderName(uploaderName);
        resp.setCreateTime(material.getCreateTime());
        return resp;
    }
}
