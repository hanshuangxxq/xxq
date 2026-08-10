package com.xrq.xxq.module.practice.internship.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportReviewRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportSubmitRequest;
import com.xrq.xxq.module.practice.internship.entity.InternshipReport;

/**
 * 实习成果报告服务：学生提交（含文件）、院系管理者/教务评审、下载。
 */
public interface InternshipReportService {

    InternshipReportResponse submit(Long studentUserId, InternshipReportSubmitRequest request, MultipartFile file);

    InternshipReportResponse review(Long reportId, InternshipReportReviewRequest request, Long reviewerUserId, String userType);

    List<InternshipReportResponse> listMyReports(Long studentUserId);

    PageResult<InternshipReportResponse> listForHandler(Long handlerUserId, String userType,
                                                        ReportStatusEnum status, PageQuery pageQuery);

    InternshipReport loadForDownload(Long reportId, Long operatorUserId, String userType);

    /**
     * 删除实习报告。
     * <ul>
     *   <li>教务：全权。</li>
     *   <li>院系管理者：仅本人负责实习下的报告。</li>
     *   <li>学生：仅本人且状态为 SUBMITTED（提交后未评审前可撤回）。</li>
     * </ul>
     */
    void deleteReport(Long reportId, Long operatorUserId, String userType);
}
