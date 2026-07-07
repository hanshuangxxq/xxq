package com.xrq.xxq.module.scheduling.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问题事实（不可变输入）：上课学生所属的班级。
 * <p>
 * 支持合班/重修场景——一个课堂可能有多个班级的学生同时上课，
 * 这些班级中任意一个在其他课堂出现即构成冲突。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentGroup {

    /** 唯一标识 */
    private Long id;

    /** 班级名称（如"计科2201"） */
    private String name;

    /** 所属学院 */
    private String college;

    /** 该班级的学生人数（用于教室容量约束判断） */
    private int studentCount;
}
