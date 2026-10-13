package com.xrq.xxq.module.coursework.material.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程资料文件夹:自引用单表,任意深度,挂授课组锚点行。
 * 不变式(应用层强制):parentId 为空 = 根层文件夹;非空时父必须是同锚点文件夹;
 * 文件夹不可换父级(只能改名/删除/建子级),天然无环。
 */
@Data
@TableName("course_material_folder")
public class CourseMaterialFolder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;        // 授课组锚点行 teach_info.id(所有层级冗余同值)
    private Long parentId;           // NULL=根层;非空=父文件夹 id
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
