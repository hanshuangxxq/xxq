package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xrq.xxq.module.course.entity.CurseEnum;

import lombok.Data;

/**
 * 选课活动可选课程实体。
 * <p>
 * 每条记录本身就是一门独立的课程，课程信息直接录入。
 * 添加时会在 course 表生成一条衍生记录（source = SELECTION_COURSE），
 * 用于排课系统识别并关联 TimeRestriction。
 * {@code allowedGradeIds} / {@code allowedMajors} 为空表示不限；非空时按 id 列表过滤。
 */
@Data
@TableName("selection_course")
public class SelectionCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long groupId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String description;
    private CurseEnum courseType;
    private String allowedGradeIds;
    private String allowedMajors;
    private Integer capacity;
}
