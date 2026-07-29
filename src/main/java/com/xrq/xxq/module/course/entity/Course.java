package com.xrq.xxq.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程实体，与教师（Teacher）关联。
 * <p>
 * {@code teacher} 表的 {@code id} 字段。
 *
 * @类名 Course
 * @Date 2026/6/30
 */
@Data
@TableName("course")
public class Course {
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_SELECTION_CAMPAIGN = "SELECTION_CAMPAIGN";

    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键ID
    private String courseName;          // 课程名称
    private String courseCode;          // 课程代码/编号
    private Integer credit;             // 学分
    private String description;         // 课程描述
    private Integer courseHour;         // 课程学时
    private CurseEnum courseType;       // 课程类型（例如：必修、选修、公选、实践等 ）
    private String source;              // 来源：MANUAL/SELECTION_CAMPAIGN

}
