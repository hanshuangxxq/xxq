package com.xrq.xxq.module.coursework.announcement.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 课程公告：挂在授课组锚点行上；编辑不重复通知。 */
@Data
@TableName("course_announcement")
public class CourseAnnouncement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;
    private String title;
    private String content;
    private Long teacherId;          // 发布人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
