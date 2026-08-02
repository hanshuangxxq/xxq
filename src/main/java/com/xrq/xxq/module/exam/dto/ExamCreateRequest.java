package com.xrq.xxq.module.exam.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.xrq.xxq.module.exam.entity.ExamStatusEnum;
import com.xrq.xxq.module.exam.entity.ExamTypeEnum;

import lombok.Data;

/**
 * 考试创建/修改请求。期末/期中需绑 teachInfoId；补考/重修走补考创建接口。
 */
@Data
public class ExamCreateRequest {

    private String examName;
    private Long courseId;
    private Long teachInfoId;        // 期末/期中必填；补考/重修为 null
    private String className;        // 期末/期中必填：排考的单班级名；补考/重修为 null
    private ExamTypeEnum examType;
    private Long semesterId;
    private LocalDate examDate;
    private LocalTime startTime;
    private Integer durationMinutes; // 考试时长（分钟），后端据此计算 endTime
    private Long localId;            // 考试地点 local.id
    private String notes;
    private ExamStatusEnum status;   // 修改时可传；创建默认 SCHEDULED
}
