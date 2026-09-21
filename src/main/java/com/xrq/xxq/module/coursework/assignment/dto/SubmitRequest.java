package com.xrq.xxq.module.coursework.assignment.dto;

import lombok.Data;

/** 学生提交作业请求（multipart 的 data 部分）。 */
@Data
public class SubmitRequest {

    /** 文本作答（与附件至少给一个，由服务端校验）。 */
    private String content;

    /** 分片产物路径（与 multipart file 二选一）。 */
    private String filePath;

    /** 附件展示名（可空）。 */
    private String fileOriginal;
}
