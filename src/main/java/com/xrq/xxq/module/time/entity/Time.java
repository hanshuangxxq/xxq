package com.xrq.xxq.module.time.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 时间类，表示一天中的具体的时间段
 */
@Data
@TableName("time")
public class Time {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalTime startPeriod; // 开始节次
    private LocalTime endPeriod;   // 结束节次
}
