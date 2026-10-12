package com.xrq.xxq.module.coursework.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程章节树节点：自引用单表，固定两级（章 CHAPTER -> 节 SECTION），挂授课组锚点行。
 * 视频挂节节点（course_video.node_id -> 本表 SECTION 行，NULL=公共区）。
 * 不变式（应用层强制）：parentId 为空当且仅当 CHAPTER；SECTION 的父必须是同锚点的 CHAPTER；
 * 节创建后不可换父章（仅可改名），故 teachInfoId 冗余列永不失配。
 */
@Data
@TableName("course_chapter_node")
public class CourseChapterNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;        // 授课组锚点行 teach_info.id（章直接挂；节冗余同值）
    private Long parentId;           // NULL=章；非空=所属章 id
    private ChapterNodeTypeEnum nodeType;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
