package com.xrq.xxq.module.coursework.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教学视频：挂在授课组锚点行上。文件为内容寻址 mp4，时长由客户端解析后回传（服务端不解析视频）。
 */
@Data
@TableName("course_video")
public class CourseVideo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;        // 授课组锚点行 teach_info.id
    private String title;
    private String description;
    private String fileName;         // objects/course-video/{sha256}.mp4
    private String fileOriginal;     // 原始文件名
    private String sha256;           // 内容摘要
    private Long sizeBytes;
    private Integer durationSec;     // 时长秒（客户端回传，可空）
    private Integer sortNo;
    private Long teacherId;          // 上传人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
