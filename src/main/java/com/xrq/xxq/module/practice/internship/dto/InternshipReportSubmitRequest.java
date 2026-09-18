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

    /** 分片上传产物路径（objects/...），与 multipart 的 file 部分二选一；大文件走这里。 */
    private String filePath;

    /** 展示文件名，配合 filePath 使用；留空则回退产物文件名。 */
    private String fileOriginal;
}
