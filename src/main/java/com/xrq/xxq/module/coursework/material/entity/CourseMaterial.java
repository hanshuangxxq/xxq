package com.xrq.xxq.module.coursework.material.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 课程资料/课件：挂在授课组锚点行上。 */
@Data
@TableName("course_material")
public class CourseMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;
    private String title;
    private String description;
    private String fileName;         // objects/ 相对路径
    private String fileOriginal;     // 原始文件名
    private String fileExt;          // 扩展名（含点，小写）
    private Long sizeBytes;
    private Long teacherId;          // 上传人 user.id
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
