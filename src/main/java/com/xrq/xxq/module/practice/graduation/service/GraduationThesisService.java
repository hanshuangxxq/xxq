package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckRegisterRequest;
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;

/**
 * 论文与查重（阶段三：提交/版本管理/形式审查/查重登记）。
 */
public interface GraduationThesisService {

    /** 学生提交/重提论文（R-8.1/R-8.2，门禁 R-3.2：开题已通过；保留最近3版） */
    ThesisResponse submitThesis(Long studentUserId, ThesisSubmitRequest request, MultipartFile file);

    /** 指导教师形式审查（R-8.3：通过进入待查重 / 退回修改） */
    ThesisResponse reviewThesis(Long teacherUserId, Long thesisId, ThesisReviewRequest request);

    /** 教务登记查重结果（R-8.5/R-8.6，历史保留） */
    DuplicateCheckResponse registerDuplicateCheck(Long academicUserId, DuplicateCheckRegisterRequest request);

    /** 学生查看我的论文（含版本列表与查重记录） */
    List<ThesisResponse> listMyThesis(Long studentUserId, Long campaignId);

    /** 教师查看名下学生的论文 */
    List<ThesisResponse> listTeacherThesis(Long teacherUserId, Long campaignId);

    /** 教务查看活动内论文（按状态筛选） */
    List<ThesisResponse> listCampaignThesis(Long campaignId, ThesisStatusEnum status);

    /** 论文文件下载（权限：学生本人/指导教师/院系/教务） */
    FileView resolveThesisFile(String userType, Long userId, Long thesisId);

    /** 论文的查重记录 */
    List<DuplicateCheckResponse> listDuplicateChecks(Long thesisId);

    /**
     * 导出查重数据包（R-8.4：xlsx 名单 + zip 论文文件包，复用导出能力）。
     * 导出动作记录操作日志。
     */
    ExportPackage exportPackage(Long academicUserId, Long campaignId, ThesisStatusEnum status);

    /**
     * 查重数据包：zip 字节 + 文件名。
     */
    record ExportPackage(byte[] data, String fileName) {
    }

    /**
     * 论文下载视图：磁盘文件 + 原始文件名。
     */
    record FileView(java.nio.file.Path path, String originalName) {
    }
}
