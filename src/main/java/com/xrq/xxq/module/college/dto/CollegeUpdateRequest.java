package com.xrq.xxq.module.college.dto;

import lombok.Data;

/**
 * 院系更新请求（教务，部分更新）。
 */
@Data
public class CollegeUpdateRequest {

    private String collegeName;
    private String collegeCode;
    private String collegeNo;
}
