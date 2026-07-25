package com.xrq.xxq.module.selection.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCampaignGroup;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionClassService;
import com.xrq.xxq.module.selection.service.SelectionCourseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionCampaignServiceImpl
        extends ServiceImpl<SelectionCampaignMapper, SelectionCampaign>
        implements SelectionCampaignService {

    private static final String SOURCE_SELECTION_CAMPAIGN = "SELECTION_CAMPAIGN";
    private static final int DEFAULT_START_WEEK = 1;
    private static final int DEFAULT_END_WEEK = 16;

    private final SelectionCourseMapper selectionCourseMapper;
    private final SelectionCampaignGroupMapper selectionCampaignGroupMapper;
    private final SelectionGroupMapper selectionGroupMapper;
    private final SemesterService semesterService;
    private final SelectionClassService selectionClassService;
    private final SelectionCourseService selectionCourseService;
    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public CampaignResponse create(CampaignCreateRequest request) {
        Semester semester = semesterService.getById(request.getSemesterId());
        if (semester == null) {
            throw new BusinessException(400, "学期不存在");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException(400, "结束时间不能早于开始时间");
        }

        // 1. 在 course 表插入衍生记录（source = SELECTION_CAMPAIGN），courseName = 活动名
        Course derivedCourse = new Course();
        derivedCourse.setCourseName(request.getName());
        derivedCourse.setCourseCode("SEL-CAMP-" + request.getSemesterId() + "-" + System.currentTimeMillis());
        derivedCourse.setSource(SOURCE_SELECTION_CAMPAIGN);
        courseMapper.insert(derivedCourse);

        // 2. 创建 campaign 并关联 courseId
        SelectionCampaign campaign = new SelectionCampaign();
        campaign.setName(request.getName());
        campaign.setSemesterId(request.getSemesterId());
        campaign.setCourseId(derivedCourse.getId());
        campaign.setStartWeek(request.getStartWeek() != null ? request.getStartWeek() : DEFAULT_START_WEEK);
        campaign.setEndWeek(request.getEndWeek() != null ? request.getEndWeek() : DEFAULT_END_WEEK);
        campaign.setStartTime(request.getStartTime());
        campaign.setEndTime(request.getEndTime());
        campaign.setStatus(CampaignStatusEnum.DRAFT);
        save(campaign);
        return toResponse(campaign, semester);
    }

    @Override
    public CampaignResponse update(Long id, CampaignUpdateRequest request) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可修改");
        }
        if (request.getName() != null) campaign.setName(request.getName());
        if (request.getSemesterId() != null) {
            Semester semester = semesterService.getById(request.getSemesterId());
            if (semester == null) {
                throw new BusinessException(400, "学期不存在");
            }
            campaign.setSemesterId(request.getSemesterId());
        }
        if (request.getStartTime() != null) campaign.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) campaign.setEndTime(request.getEndTime());
        if (request.getStartWeek() != null) campaign.setStartWeek(request.getStartWeek());
        if (request.getEndWeek() != null) campaign.setEndWeek(request.getEndWeek());
        if (campaign.getEndWeek() < campaign.getStartWeek()) {
            throw new BusinessException(400, "结束周不能早于开始周");
        }
        if (campaign.getEndTime().isBefore(campaign.getStartTime())) {
            throw new BusinessException(400, "结束时间不能早于开始时间");
        }
        updateById(campaign);
        // 同步更新衍生 course 的 courseName（若活动名变更）
        if (request.getName() != null) {
            Course derived = courseMapper.selectById(campaign.getCourseId());
            if (derived != null) {
                derived.setCourseName(request.getName());
                courseMapper.updateById(derived);
            }
        }
        return toResponse(campaign, semesterService.getById(campaign.getSemesterId()));
    }

    @Override
    public CampaignResponse getDetail(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        return toResponse(campaign, semesterService.getById(campaign.getSemesterId()));
    }

    @Override
    public List<CampaignResponse> listAll() {
        List<SelectionCampaign> list = list();
        return list.stream()
                .map(c -> toResponse(c, semesterService.getById(c.getSemesterId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<CampaignResponse> listBindableForGroup(Long groupId) {
        if (selectionGroupMapper.selectById(groupId) == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        List<SelectionCampaign> allCampaigns = list();
        if (allCampaigns.isEmpty()) {
            return List.of();
        }
        // 一个活动只能绑定一个选课组：取每条绑定关系，按活动维度归集（兼容历史多绑数据）。
        // 使用显式空 Wrapper 而非 selectList(null)，避免不同 MyBatis Plus 版本下行为差异。
        List<SelectionCampaignGroup> allBindings = selectionCampaignGroupMapper.selectList(
                new LambdaQueryWrapper<>());
        Map<Long, List<Long>> boundGroupIdsByCampaign = allBindings.stream()
                .collect(Collectors.groupingBy(
                        SelectionCampaignGroup::getCampaignId,
                        Collectors.mapping(SelectionCampaignGroup::getGroupId, Collectors.toList())));
        // 过滤规则：未绑定任何组 -> 可绑定（保留）；已绑定到本组 -> 保留以便解绑；
        //         已绑定到其它组 -> 排除，不能在本组的绑定管理中看到。
        return allCampaigns.stream()
                .filter(c -> {
                    List<Long> boundGroupIds = boundGroupIdsByCampaign.getOrDefault(c.getId(), List.of());
                    return boundGroupIds.isEmpty() || boundGroupIds.contains(groupId);
                })
                .map(c -> {
                    CampaignResponse resp = toResponse(c, semesterService.getById(c.getSemesterId()));
                    if (boundGroupIdsByCampaign.getOrDefault(c.getId(), List.of()).contains(groupId)) {
                        resp.setBoundGroupId(groupId);
                    }
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可删除");
        }
        // 级联删除：可选课程（含衍生 course 与 TimeRestriction）-> 活动-组绑定关系 -> 活动 -> 活动衍生 course
        // 注意：选课组本身是独立实体，可能被其他活动复用，不删除
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, id));
        for (SelectionCourse sc : courses) {
            selectionCourseService.remove(id, sc.getId());
        }
        selectionCampaignGroupMapper.delete(new LambdaQueryWrapper<SelectionCampaignGroup>()
                .eq(SelectionCampaignGroup::getCampaignId, id));
        Long campaignCourseId = campaign.getCourseId();
        removeById(id);
        if (campaignCourseId != null) {
            courseMapper.deleteById(campaignCourseId);
        }
    }

    @Override
    public void open(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可开启");
        }
        campaign.setStatus(CampaignStatusEnum.OPEN);
        updateById(campaign);
    }

    @Override
    public void close(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "仅开放状态的活动可关闭");
        }
        campaign.setStatus(CampaignStatusEnum.CLOSED);
        updateById(campaign);
    }

    @Override
    @Transactional
    public void finalizeCampaign(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.CLOSED) {
            throw new BusinessException(409, "仅关闭状态的活动可分班");
        }
        selectionClassService.finalize(id);
        campaign.setStatus(CampaignStatusEnum.FINALIZED);
        updateById(campaign);
    }

    private CampaignResponse toResponse(SelectionCampaign campaign, Semester semester) {
        CampaignResponse resp = new CampaignResponse();
        resp.setId(campaign.getId());
        resp.setName(campaign.getName());
        resp.setSemesterId(campaign.getSemesterId());
        resp.setSemesterName(semester != null ? semester.getName() : null);
        resp.setCourseId(campaign.getCourseId());
        resp.setStartWeek(campaign.getStartWeek());
        resp.setEndWeek(campaign.getEndWeek());
        resp.setStartTime(campaign.getStartTime());
        resp.setEndTime(campaign.getEndTime());
        resp.setStatus(campaign.getStatus());
        resp.setCreateTime(campaign.getCreateTime());
        Long courseCount = selectionCourseMapper.selectCount(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaign.getId()));
        resp.setSelectedCourseCount(courseCount.intValue());
        return resp;
    }
}
