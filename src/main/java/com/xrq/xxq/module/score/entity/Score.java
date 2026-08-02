package com.xrq.xxq.module.score.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 成绩记录。
 * <p>
 * 按 (teach_info_id, student_user_id, grade_type) 唯一：同一学生在同一授课安排下，
 * 正常(REGULAR)/补考(MAKEUP)/重修(RETAKE) 成绩各一条。录入即生效，total_score 与
 * grade_level 由服务端按 score_config 占比派生。
 */
@Data
@TableName("score")
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;        // FK -> teach_info.id
    private Long courseId;           // 冗余，便于统计/导出
    private Long teacherId;          // 授课教师 teacher.id（快照）
    private Long studentUserId;      // 学生 user.id
    private Long semesterId;         // FK -> semester.id
    private BigDecimal regularScore; // 平时分
    private BigDecimal finalScore;   // 期末成绩
    private Integer regularRatio;    // 录入时平时占比快照（审计）
    private BigDecimal totalScore;   // 总评成绩（派生）
    private String scoreLevel;       // 等级：优/良/中/及格/不及格（派生）
    private ScoreTypeEnum scoreType; // REGULAR/MAKEUP/RETAKE
    private Long originalScoreId;    // 补考/重修指向原不及格 grade.id
    private Integer locked;          // 0:可修改 1:已锁定
    private Long enterUserId;        // 录入人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;         // 0:未删除 1:已删除
}
