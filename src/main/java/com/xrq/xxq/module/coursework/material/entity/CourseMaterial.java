package com.xrq.xxq.module.coursework.material.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)   // 移动资料允许把文件夹改回 null(根目录)
    private Long folderId;           // 所在文件夹 course_material_folder.id(NULL=根目录)
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
