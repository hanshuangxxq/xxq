package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 答辩安排响应。
 */
@Data
public class DefenseResponse {

    private Long id;

    private Long campaignId;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private String groupName;

    private LocalDateTime defenseTime;

    private String location;

    private Long reviewerId;

    private String reviewerName;

    private List<Long> defenseTeacherIds;

    private List<String> defenseTeacherNames;

    /** 答辩材料存储路径（有值即表示可下载） */
    private String fileName;

    /** 答辩材料展示文件名 */
    private String fileOriginal;
}
