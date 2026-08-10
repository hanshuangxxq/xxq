package com.xrq.xxq.module.college.dto;

import lombok.Data;

/**
 * 院系创建请求（教务）。
 */
@Data
public class CollegeCreateRequest {

    private String collegeName;     // 院系名称（必填，唯一）
    private String collegeCode;     // 院系代码
    private String collegeNo;       // 院系编号
}
