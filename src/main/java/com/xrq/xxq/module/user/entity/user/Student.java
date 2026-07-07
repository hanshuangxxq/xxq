package com.xrq.xxq.module.user.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键id
    private Long userId;                // FK → user.id
    private String studentNo;           // 学号
    private String grade;               // 年级
    private Long majorId;               // FK → major.id
    private Long classId;           // 班级id
    private Integer enrollmentYear;    // 入学年份
}
