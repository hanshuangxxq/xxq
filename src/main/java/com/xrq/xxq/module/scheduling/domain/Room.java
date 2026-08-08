package com.xrq.xxq.module.scheduling.domain;

import com.xrq.xxq.module.local.entity.LocalTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问题事实（不可变输入）：对应 local 表，表示一间教室。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    /** 教室ID，对应 local.id */
    private Long id;

    /** 教学楼，对应 local.building */
    private String building;

    /** 教室名称，对应 local.class_room */
    private String roomName;

    /** 教室最大容量（人数），对应 local.max。0 或 Integer.MAX_VALUE 表示无限制 */
    private int capacity;

    /** 教室类型，对应 local.type。自动排课仅选取普通教室 */
    private LocalTypeEnum type;
}
