package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课记录实体。
 */
@Data
@TableName("selection_record")
public class SelectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long studentId;
    private Long selectionCourseId;
    private RecordStatusEnum status;
    private LocalDateTime selectTime;
    private LocalDateTime dropTime;
}
