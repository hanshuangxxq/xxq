package com.xrq.xxq.module.score.dto;

import java.util.List;

import lombok.Data;

/**
 * 批量成绩录入请求：针对一条授课安排，批量 upsert 学生成绩。
 */
@Data
public class ScoreBatchRequest {

    private Long teachInfoId;                 // 授课安排 id
    private Long examId;                      // 可选：按考试排考班级限定可录入学生（合班时仅允许该考试班级）
    private List<ScoreEntryRequest> entries;  // 学生成绩列表
}
