package com.xrq.xxq.module.selection.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionCourseAddRequest;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 选课活动可选课程管理接口。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/courses")
@RequiredArgsConstructor
public class SelectionCourseController {

    private final SelectionCampaignService campaignService;
    private final SelectionCourseMapper selectionCourseMapper;
    private final CourseMapper courseMapper;

    @PostMapping
    public Result<SelectionCourse> add(@PathVariable Long campaignId,
                                       @RequestBody SelectionCourseAddRequest request) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可配置可选课程");
        }
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(400, "课程不存在");
        }
        if (request.getCapacity() <= 0) {
            throw new BusinessException(400, "容量必须大于0");
        }
        Long exists = selectionCourseMapper.selectCount(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, campaignId)
                .eq(SelectionCourse::getCourseId, request.getCourseId()));
        if (exists > 0) {
            throw new BusinessException(409, "该课程已在活动中");
        }
        SelectionCourse sc = new SelectionCourse();
        sc.setCampaignId(campaignId);
        sc.setCourseId(request.getCourseId());
        sc.setCapacity(request.getCapacity());
        selectionCourseMapper.insert(sc);
        return Result.ok(sc);
    }

    @GetMapping
    public Result<List<SelectionCourseResponse>> list(@PathVariable Long campaignId,
                                                      HttpServletRequest request) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        if (courses.isEmpty()) {
            return Result.ok(List.of());
        }
        List<Long> courseIds = courses.stream().map(SelectionCourse::getCourseId).toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        return Result.ok(courses.stream().map(sc -> toResponse(sc, courseMap.get(sc.getCourseId()))).toList());
    }

    @DeleteMapping("/{courseId}")
    public Result<Void> remove(@PathVariable Long campaignId, @PathVariable Long courseId) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可移除可选课程");
        }
        selectionCourseMapper.delete(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, campaignId)
                .eq(SelectionCourse::getCourseId, courseId));
        return Result.ok();
    }

    private SelectionCourseResponse toResponse(SelectionCourse sc, Course course) {
        SelectionCourseResponse resp = new SelectionCourseResponse();
        resp.setId(sc.getId());
        resp.setCampaignId(sc.getCampaignId());
        resp.setCourseId(sc.getCourseId());
        resp.setCapacity(sc.getCapacity());
        if (course != null) {
            resp.setCourseName(course.getCourseName());
            resp.setCourseCode(course.getCourseCode());
            resp.setCredit(course.getCredit());
            resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
        }
        resp.setSelectedCount(0);
        resp.setRemaining(sc.getCapacity());
        resp.setSelectedByMe(false);
        return resp;
    }
}
