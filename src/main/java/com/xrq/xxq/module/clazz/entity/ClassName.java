package com.xrq.xxq.module.clazz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 班级名称类。
 * <p>
 * 归属链：class_name -> major.college_id -> college.id。班级只挂专业，
 * 院系由专业推导（见 {@code ClassNameService.toCollegeIdMap}），本表不再存 college_id，
 * 以免与专业所属院系自相矛盾。
 */
@Data
@TableName("class_name")
public class ClassName {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String className; // 班级名称
    private Long majorId; // 所属专业 -> major.id
}
