package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕设活动资料（教务下发的模板/规范等附件，一个活动可挂多份）。
 */
@Data
@TableName("graduation_campaign_material")
public class GraduationCampaignMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 存储相对路径（objects/graduation-campaign-material/...） */
    private String fileName;

    /** 展示文件名 */
    private String fileOriginal;

    /** 上传人 user.id */
    private Long uploaderId;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
