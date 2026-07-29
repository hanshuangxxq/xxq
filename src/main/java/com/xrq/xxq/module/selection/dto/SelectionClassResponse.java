package com.xrq.xxq.module.selection.dto;

import java.util.List;

import lombok.Data;

@Data
public class SelectionClassResponse {
    private Long classId;
    private String courseName;
    private Integer classNo;
    private Integer studentCount;
    private Long teacherId;
    private String teacherName;
    private List<StudentSelectionDto> members;
}
