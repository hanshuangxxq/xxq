package com.xrq.xxq.module.user.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键id
    private Long userId;                // FK → user.id
    private String teacherNo;           // 教师编号
    private String title;               // 职称
    private Long collegeId;             // 所属院系 -> college.id
    private LocalDate hireDate;         // 参加工作日期
}
