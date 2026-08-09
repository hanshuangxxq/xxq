package com.xrq.xxq.module.practice.socialpractice.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;

import lombok.Data;

/**
 * 社会实践报告实体（学生提交 + 教务评审）。
 */
@Data
@TableName("social_practice_report")
public class SocialPracticeReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long practiceId;
    private Long studentId;               // 学生 user.id
    private String title;
    private String summary;               // 实践总结
    private String fileName;              // 磁盘存储名
    private String fileOriginal;          // 原始文件名
    private LocalDateTime submitTime;
    private Integer score;
    private String feedback;
    private LocalDateTime reviewTime;
    private ReportStatusEnum status;

    @TableLogic
    private Integer deleted;
}
