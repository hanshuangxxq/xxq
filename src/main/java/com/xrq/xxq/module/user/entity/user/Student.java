package com.xrq.xxq.module.user.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 学生实体。
 * <p>
 * 归属链：student -> class_name.major_id -> major.college_id -> college.id。
 * 专业不单独存本表（避免与班级所属专业矛盾），经班级推导。
 * {@code gradeId} 与 {@code enrollmentYear} 并存：留级等场景下年级与入学年份不对等，
 * 前者是当前学业年级、后者是学籍入学年份，不可互相推导。
 */
@Data
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键id
    private Long userId;                // FK -> user.id
    private String studentNo;           // 学号
    private Long gradeId;               // FK -> grade.id（当前学业年级）
    private Long classId;               // FK -> class_name.id（专业/院系随班级推导）
    private Integer enrollmentYear;     // 入学年份（学籍口径，与 gradeId 不等价）
}
