package com.xrq.xxq.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("semester")
public class Semester {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer startWeek;
    private Integer endWeek;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
