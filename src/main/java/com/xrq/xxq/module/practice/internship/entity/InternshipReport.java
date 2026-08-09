package com.xrq.xxq.module.practice.internship.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;

import lombok.Data;

/**
 * 实习成果报告实体（学生提交 + 教师评审）。
 * <p>
 * file_name 为磁盘存储名，file_original 为原始文件名。
 */
@Data
@TableName("internship_report")
public class InternshipReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long internshipId;
    private Long studentId;             // 学生 user.id
    private String title;
    private String summary;             // 实习总结
    private String fileName;            // 磁盘存储名
    private String fileOriginal;        // 原始文件名
    private LocalDateTime submitTime;
    private Integer score;              // 评分
    private String feedback;            // 反馈意见
    private LocalDateTime reviewTime;
    private ReportStatusEnum status;

    @TableLogic
    private Integer deleted;
}
