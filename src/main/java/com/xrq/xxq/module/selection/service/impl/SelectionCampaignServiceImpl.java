package com.xrq.xxq.module.selection.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionClassService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionCampaignServiceImpl
        extends ServiceImpl<SelectionCampaignMapper, SelectionCampaign>
        implements SelectionCampaignService {

    private final SelectionCourseMapper selectionCourseMapper;
    private final SemesterService semesterService;
    private final SelectionClassService selectionClassService;

    @Override
    public CampaignResponse create(CampaignCreateRequest request) {
        Semester semester = semesterService.getById(request.getSemesterId());
        if (semester == null) {
            throw new BusinessException(400, "学期不存在");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException(400, "结束时间不能早于开始时间");
        }
        SelectionCampaign campaign = new SelectionCampaign();
        campaign.setName(request.getName());
        campaign.setSemesterId(request.getSemesterId());
        campaign.setStartTime(request.getStartTime());
        campaign.setEndTime(request.getEndTime());
        campaign.setMaxCoursesPerStudent(
                request.getMaxCoursesPerStudent() == null ? 1 : request.getMaxCoursesPerStudent());
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
        if (request.getMaxCoursesPerStudent() != null) {
            campaign.setMaxCoursesPerStudent(request.getMaxCoursesPerStudent());
        }
        if (campaign.getEndTime().isBefore(campaign.getStartTime())) {
            throw new BusinessException(400, "结束时间不能早于开始时间");
        }
        updateById(campaign);
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
    @Transactional
    public void delete(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可删除");
        }
        selectionCourseMapper.delete(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, id));
        removeById(id);
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
        Long courseCount = selectionCourseMapper.selectCount(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, id));
        if (courseCount == 0) {
            throw new BusinessException(409, "活动未配置可选课程，无法开启");
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
        resp.setStartTime(campaign.getStartTime());
        resp.setEndTime(campaign.getEndTime());
        resp.setMaxCoursesPerStudent(campaign.getMaxCoursesPerStudent());
        resp.setStatus(campaign.getStatus());
        resp.setCreateTime(campaign.getCreateTime());
        Long courseCount = selectionCourseMapper.selectCount(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaign.getId()));
        resp.setSelectedCourseCount(courseCount.intValue());
        return resp;
    }
}
