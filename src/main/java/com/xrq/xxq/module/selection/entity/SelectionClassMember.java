package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课班成员实体。
 */
@Data
@TableName("selection_class_member")
public class SelectionClassMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private Long studentId;
    private Long recordId;
}
