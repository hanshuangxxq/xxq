package com.xrq.xxq.module.college.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 院系实体。
 * <p>
 * 院系主实体：class_name / teacher / department(院系管理员) / major 通过 college_id 引用本表。
 * college_name 唯一性由应用层（CollegeService）校验，DB 仅保留普通索引。
 */
@Data
@TableName("college")
public class College {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String collegeName;     // 院系名称
    private String collegeCode;     // 院系代码
    private String collegeNo;       // 院系编号
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
