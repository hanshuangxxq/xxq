package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 论文提交请求（文件单独以 multipart 传输）。
 */
@Data
public class ThesisSubmitRequest {

    private Long assignmentId;
    private String title;
    private String abstractText;
}
