package com.xrq.xxq.module.practice.socialpractice.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 社会实践项目实体（教务发布）。
 * <p>
 * selected_count 记录已申报人数（含待审核），用于容量控制。
 */
@Data
@TableName("social_practice")
public class SocialPractice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private String title;
    private String description;
    private String organizer;             // 主办/组织方
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer selectedCount;
    private SocialPracticeStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
