package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 毕业选题导出行（教务导出送查重用）。
 * <p>含全部已申报学生：已匹配附教师信息，未匹配教师信息为空。
 */
@Data
public class GraduationExportRow {

    private String studentNo;
    private String studentName;
    private String collegeName;
    private String proposalTitle;
    private String teacherNo;
    private String teacherName;
    private String source;          // 教师自选/院系分配/未匹配
    private String status;          // 匹配状态
}
