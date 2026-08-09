package com.xrq.xxq.module.practice.socialpractice.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportReviewRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportSubmitRequest;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeReport;

/**
 * 社会实践报告服务：学生提交（含文件）、教务评审、下载。
 */
public interface SocialPracticeReportService {

    SocialPracticeReportResponse submit(Long studentUserId, SocialPracticeReportSubmitRequest request, MultipartFile file);

    SocialPracticeReportResponse review(Long reportId, SocialPracticeReportReviewRequest request);

    List<SocialPracticeReportResponse> listMyReports(Long studentUserId);

    PageResult<SocialPracticeReportResponse> listForHandler(ReportStatusEnum status, PageQuery pageQuery);

    SocialPracticeReport loadForDownload(Long reportId, Long operatorUserId, String userType);

    /**
     * 删除社会实践报告。
     * <ul>
     *   <li>教务：全权。</li>
     *   <li>学生：仅本人且状态为 SUBMITTED（提交后未评审前可撤回）。</li>
     * </ul>
     */
    void deleteReport(Long reportId, Long operatorUserId, String userType);
}
