package com.xrq.xxq.module.college.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 院系响应。
 */
@Data
public class CollegeResponse {

    private Long id;
    private String collegeName;
    private String collegeCode;
    private String collegeNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
