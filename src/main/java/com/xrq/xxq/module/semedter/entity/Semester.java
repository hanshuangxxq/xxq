package com.xrq.xxq.module.semedter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学期信息类
 */
@Data
@TableName("semester")
public class Semester {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name; //该学期的名
    private Integer startWeek;
    private Integer endWeek;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
