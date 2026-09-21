package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 作业创建/修改请求（multipart 的 data 部分；附件经 filePath 或 file 部分携带）。 */
@Data
public class AssignmentSaveRequest {

    /** 授课安排 id（接受组内任意行，服务端归一到锚点）。创建必填，修改忽略。 */
    private Long teachInfoId;

    @NonNull
    private String title;

    private String content;

    /** 分片产物路径（与 multipart file 二选一）。 */
    private String filePath;

    /** 附件展示名（可空）。 */
    private String fileOriginal;

    @NonNull
    private LocalDateTime deadline;

    /** 满分，默认 100。已发布后不可修改。 */
    private BigDecimal totalScore;

    /** true=创建后直接发布；修改时忽略。 */
    private Boolean publish;
}
