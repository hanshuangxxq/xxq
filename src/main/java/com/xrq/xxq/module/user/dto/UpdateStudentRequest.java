package com.xrq.xxq.module.user.dto;

import lombok.Data;

/**
 * 更新学生信息的请求类
 */
@Data
public class UpdateStudentRequest {
    private String studentNo;
    private String className;
    private String majorName;
    private Integer enrollmentYear;
}
