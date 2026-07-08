package com.xrq.xxq.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 TeachInfo
 * @Date 2026/6/30
 * 教学信息，方便直接查询课表
 */
@Data
@TableName("teach_info")
public class TeachInfo {
    @TableId (type = IdType.AUTO)
    private Long id;
    private Long courseId; // 课程ID
    private Long teacherId; // 教师ID
    private String className; // 班级名称
    private Long timeId;  // FK → time.id  上课时间段
    private Long localId; // FK → local.id 上课地点
    private Integer dayOfWeek; // 星期几
    private Integer startWeek; // 起始教学周
    private Integer endWeek;   // 结束教学周
    private Long semesterId;   // FK → semester.id

}
