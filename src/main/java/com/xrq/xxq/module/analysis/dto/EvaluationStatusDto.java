package com.xrq.xxq.module.analysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 评教周期状态：学生评教页据此判断是否展示「暂无评教」。
 * <p>学生且已开放时，附带可评课程列表（含课程名，解决公选课不知课程名的问题）。
 */
@Data
public class EvaluationStatusDto {

    private Boolean open;          // 评教是否开放
    private String message;        // 未开放时的提示文案，如「暂无评教」
    private Long semesterId;
    private String semesterName;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private List<EvaluableCourse> courses;  // 开放时返回学生可评课程（含课程名）

    /** 可评课程条目。 */
    @Data
    public static class EvaluableCourse {
        private Long teachInfoId;
        private Long courseId;
        private String courseName;
        private String teacherName;
        private Boolean evaluated;   // 是否已提交评教
    }
}
