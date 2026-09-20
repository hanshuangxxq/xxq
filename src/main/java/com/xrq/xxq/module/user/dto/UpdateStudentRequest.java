package com.xrq.xxq.module.user.dto;

import lombok.Data;

/**
 * 更新学生信息的请求类。
 * <p>
 * 无 majorName：专业挂在班级上（class_name.major_id），改专业只能通过换班级，
 * 单独改专业会让学生的专业与班级所属专业相矛盾。
 */
@Data
public class UpdateStudentRequest {
    private String studentNo;
    private String className;
    private String gradeName;
    private Integer enrollmentYear;
}
