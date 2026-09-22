package com.xrq.xxq.module.coursework.assignment.dto;

import java.time.LocalDateTime;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 克隆作业到本人其它授课组（题目快照随源作业复制，落为 DRAFT 需另行发布）。 */
@Data
public class CloneAssignmentRequest {

    @NonNull
    private Long teachInfoId;

    @NonNull
    private LocalDateTime deadline;
}
