package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.practice.graduation.dto.GuidanceLogCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.GuidanceLogResponse;
import com.xrq.xxq.module.practice.graduation.dto.MidtermResponse;
import com.xrq.xxq.module.practice.graduation.dto.MidtermReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.MidtermSubmitRequest;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportResponse;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportSubmitRequest;

/**
 * 过程管理（阶段二：开题报告 / 中期检查 / 过程指导记录）。
 */
public interface GraduationProcessService {

    /** 学生提交/重提开题报告（R-7.1，门禁 R-3.1：选题审批完毕且已确定指导教师） */
    OpeningReportResponse submitOpeningReport(Long studentUserId, OpeningReportSubmitRequest request,
                                              MultipartFile file);

    /** 指导教师审核开题报告（R-7.2：通过/退回修改） */
    OpeningReportResponse reviewOpeningReport(Long teacherUserId, Long reportId,
                                              OpeningReportReviewRequest request);

    /** 学生查看我的开题报告 */
    OpeningReportResponse getMyOpeningReport(Long studentUserId, Long campaignId);

    /** 教师查看名下学生的开题报告（含审核） */
    List<OpeningReportResponse> listOpeningReportsByTeacher(Long teacherUserId, Long campaignId);

    /** 学生提交中期检查（R-7.4，门禁：开题已通过） */
    MidtermResponse submitMidterm(Long studentUserId, MidtermSubmitRequest request, MultipartFile file);

    /** 指导教师审核中期并给出结论（R-7.5） */
    MidtermResponse reviewMidterm(Long teacherUserId, Long midtermId, MidtermReviewRequest request);

    /** 学生查看我的中期检查 */
    MidtermResponse getMyMidterm(Long studentUserId, Long campaignId);

    /** 教师查看名下学生的中期检查 */
    List<MidtermResponse> listMidtermsByTeacher(Long teacherUserId, Long campaignId);

    /** 教师记录过程指导日志（R-7.7，仅限名下学生） */
    GuidanceLogResponse createGuidanceLog(Long teacherUserId, GuidanceLogCreateRequest request);

    /** 查看指导记录（教师：本人名下；教务/院系：活动维度，院系按本院系过滤） */
    List<GuidanceLogResponse> listGuidanceLogs(Long campaignId, Long studentId,
                                               String userType, Long operatorUserId);

    /** 下载开题报告附件（权限：学生本人/指导教师/院系/教务） */
    FileView resolveOpeningReportFile(String userType, Long userId, Long reportId);

    /** 下载中期检查附件（权限同上） */
    FileView resolveMidtermFile(String userType, Long userId, Long midtermId);

    /**
     * 附件下载视图：磁盘文件 + 原始文件名。
     */
    record FileView(java.nio.file.Path path, String originalName) {
    }
}
