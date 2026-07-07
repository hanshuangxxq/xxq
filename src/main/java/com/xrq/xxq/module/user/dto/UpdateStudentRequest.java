package com.xrq.xxq.module.user.dto;

import lombok.Data;

@Data
public class UpdateStudentRequest {
    private String studentNo;
    private String className;
    private String majorName;
    private Integer enrollmentYear;
}
