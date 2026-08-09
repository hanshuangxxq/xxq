package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕业论文实体（学生提交 + 教师评审）。
 * <p>
 * file_name 为磁盘存储名（UUID），file_original 为原始文件名（展示/下载用）。
 * 学生须有 APPROVED 的选题申请后方可提交论文。
 */
@Data
@TableName("thesis")
public class Thesis {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long selectionId;
    private Long studentId;          // 学生 user.id
    private Long teacherId;          // 指导教师 user.id
    private String title;
    private String abstractText;     // 摘要
    private String fileName;         // 磁盘存储名
    private String fileOriginal;     // 原始文件名
    private LocalDateTime submitTime;
    private ThesisStatusEnum status;
    private Integer reviewScore;     // 评审分数
    private String reviewComment;    // 评审意见
    private LocalDateTime reviewTime;

    @TableLogic
    private Integer deleted;
}
