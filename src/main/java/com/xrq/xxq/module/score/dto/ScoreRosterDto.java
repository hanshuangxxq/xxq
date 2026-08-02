package com.xrq.xxq.module.score.dto;

import lombok.Data;

/**
 * 成绩录入名单项：学生 user.id + 姓名 + 学号。
 */
@Data
public class ScoreRosterDto {

    private Long studentUserId;
    private String studentName;
    private String studentNo;
}
