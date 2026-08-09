package com.xrq.xxq.module.practice.internship.dto;

import lombok.Data;

/**
 * 实习成果报告提交请求（文件单独以 multipart 传输）。
 */
@Data
public class InternshipReportSubmitRequest {

    private Long internshipId;
    private String title;
    private String summary;
}
