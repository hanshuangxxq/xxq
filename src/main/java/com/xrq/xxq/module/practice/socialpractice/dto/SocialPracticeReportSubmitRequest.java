package com.xrq.xxq.module.practice.socialpractice.dto;

import lombok.Data;

/**
 * 社会实践报告提交请求（文件单独以 multipart 传输）。
 */
@Data
public class SocialPracticeReportSubmitRequest {

    private Long practiceId;
    private String title;
    private String summary;
}
