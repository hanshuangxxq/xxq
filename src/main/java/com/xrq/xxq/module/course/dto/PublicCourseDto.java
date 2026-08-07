package com.xrq.xxq.module.course.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 公选课视图（来自选课活动）。
 * <p>
 * 选课班分班后产生 teach_info 记录，查询时富化选课活动专属字段，
 * 前端无需再调 selection 接口合并，减少不必要转化。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("PUBLIC")
public class PublicCourseDto extends CourseDto {

    /** 选课活动 ID（selection_campaign.id） */
    private Long campaignId;

    /** 活动状态（CampaignStatusEnum.code：DRAFT/OPEN/CLOSED/FINALIZED） */
    private String campaignStatus;

    /** 活动容量上限 */
    private Integer capacity;

    /** 已分班人数（selection_class.student_count） */
    private Integer selectedCount;

    /** 选课班号（同一活动按容量切分，从 1 开始） */
    private Integer classNo;
}
