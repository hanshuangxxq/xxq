package com.xrq.xxq.module.selection.dto;

import java.util.List;

import lombok.Data;

/**
 * 学生端按组聚合的可选课程响应。
 */
@Data
public class StudentCourseGroupResponse {
    private Long groupId;
    private String groupName;
    private Integer groupMax;
    private Integer selectedInGroup;
    private List<SelectionCourseResponse> courses;
}
