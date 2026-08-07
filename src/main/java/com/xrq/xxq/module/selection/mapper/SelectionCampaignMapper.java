package com.xrq.xxq.module.selection.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;

@Mapper
public interface SelectionCampaignMapper extends BaseMapper<SelectionCampaign> {

    /**
     * 统计学期内使用指定 courseCode 的选课活动数量（直接查 selection_campaign.course_code）。
     * 用于 create/update 校验课程编号在学期内唯一。excludeId 用于更新时排除自身。
     */
    @Select("SELECT COUNT(*) FROM selection_campaign "
            + "WHERE semester_id = #{semesterId} AND course_code = #{courseCode} "
            + "AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    Long countByCourseCodeInSemester(@Param("semesterId") Long semesterId,
                                     @Param("courseCode") String courseCode,
                                     @Param("excludeId") Long excludeId);
}
