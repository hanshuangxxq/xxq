package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.CampaignCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignResponse;
import com.xrq.xxq.module.practice.graduation.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationCampaignService;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.util.ParamValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationCampaignServiceImpl
        extends ServiceImpl<GraduationCampaignMapper, GraduationCampaign>
        implements GraduationCampaignService {

    private final StudentMapper studentMapper;
    private final GraduationLogService logService;

    @Override
    @Transactional
    public CampaignResponse createCampaign(Long operatorUserId, String operatorType, CampaignCreateRequest request) {
        ParamValidator.requireNonBlank(request.getName(), "活动名称");
        ParamValidator.requireNonNull(request.getAllowedGradeIds(), "参与年级");
        if (request.getAllowedGradeIds().isEmpty()) {
            throw new BusinessException(400, "参与年级不能为空");
        }
        validateFields(request.getTopicStartTime(), request.getTopicEndTime(),
                request.getSupervisorCapacity(), request.getFreeSelectCapacity(),
                request.getOpeningStartTime(), request.getOpeningEndTime(),
                request.getMidtermStartTime(), request.getMidtermEndTime(),
                request.getThesisStartTime(), request.getThesisEndTime());
        Long duplicate = baseMapper.selectCount(new LambdaQueryWrapper<GraduationCampaign>()
                .eq(GraduationCampaign::getName, request.getName()));
        if (duplicate > 0) {
            throw new BusinessException(409, "同年度已存在同名活动");
        }
        validateGradeConflict(request.getAllowedGradeIds(), null);

        GraduationCampaign campaign = new GraduationCampaign();
        campaign.setName(request.getName());
        campaign.setAllowedGradeIds(String.join(",", request.getAllowedGradeIds().stream()
                .map(String::valueOf).toList()));
        campaign.setTopicStartTime(request.getTopicStartTime());
        campaign.setTopicEndTime(request.getTopicEndTime());
        campaign.setSupervisorCapacity(request.getSupervisorCapacity());
        campaign.setFreeSelectCapacity(request.getFreeSelectCapacity());
        campaign.setOpeningStartTime(request.getOpeningStartTime());
        campaign.setOpeningEndTime(request.getOpeningEndTime());
        campaign.setMidtermStartTime(request.getMidtermStartTime());
        campaign.setMidtermEndTime(request.getMidtermEndTime());
        campaign.setThesisStartTime(request.getThesisStartTime());
        campaign.setThesisEndTime(request.getThesisEndTime());
        campaign.setAdvisorWeight(request.getAdvisorWeight() != null ? request.getAdvisorWeight() : 30);
        campaign.setReviewerWeight(request.getReviewerWeight() != null ? request.getReviewerWeight() : 20);
        campaign.setDefenseWeight(request.getDefenseWeight() != null ? request.getDefenseWeight() : 50);
        if (campaign.getAdvisorWeight() + campaign.getReviewerWeight() + campaign.getDefenseWeight() != 100) {
            throw new BusinessException(400, "成绩权重之和必须为100");
        }
        campaign.setStatus(CampaignStatusEnum.DRAFT);
        save(campaign);

        logService.record(campaign.getId(), operatorUserId, operatorType, "创建毕设活动",
                "graduation_campaign", campaign.getId(), "活动名称: " + campaign.getName());
        return toResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse updateCampaign(Long operatorUserId, String operatorType, Long id, CampaignUpdateRequest request) {
        GraduationCampaign campaign = requireCampaign(id);
        LocalDateTime now = LocalDateTime.now();
        Boolean started = now.isAfter(campaign.getTopicStartTime());
        if (started) {
            // R-4.1：选题开始后仅允许延长截止时间、上调名额
            if (request.getName() != null && !request.getName().equals(campaign.getName())) {
                throw new BusinessException(409, "选题开始后不可修改活动名称");
            }
            if (request.getAllowedGradeIds() != null
                    && !sameGradeIds(request.getAllowedGradeIds(), campaign.getAllowedGradeIds())) {
                throw new BusinessException(409, "选题开始后不可修改参与年级");
            }
            if (request.getTopicStartTime() != null
                    && !request.getTopicStartTime().equals(campaign.getTopicStartTime())) {
                throw new BusinessException(409, "选题开始后不可修改选题开始时间");
            }
            if (request.getTopicEndTime() != null
                    && request.getTopicEndTime().isBefore(campaign.getTopicEndTime())) {
                throw new BusinessException(409, "选题开始后仅允许延长截止时间");
            }
            if (request.getSupervisorCapacity() != null
                    && request.getSupervisorCapacity() < campaign.getSupervisorCapacity()) {
                throw new BusinessException(409, "选题开始后仅允许上调可分配名额");
            }
            if (request.getFreeSelectCapacity() != null
                    && request.getFreeSelectCapacity() < campaign.getFreeSelectCapacity()) {
                throw new BusinessException(409, "选题开始后仅允许上调自由选择名额");
            }
            applyUpdate(campaign, request);
            if (campaign.getFreeSelectCapacity() > campaign.getSupervisorCapacity()) {
                throw new BusinessException(400, "自由选择上限不能超过可分配上限");
            }
        } else {
            // 未开始：全字段可改，走创建同款校验
            if (request.getName() != null) {
                ParamValidator.requireNonBlank(request.getName(), "活动名称");
                Long duplicate = baseMapper.selectCount(new LambdaQueryWrapper<GraduationCampaign>()
                        .eq(GraduationCampaign::getName, request.getName())
                        .ne(GraduationCampaign::getId, id));
                if (duplicate > 0) {
                    throw new BusinessException(409, "同年度已存在同名活动");
                }
            }
            if (request.getAllowedGradeIds() != null && !request.getAllowedGradeIds().isEmpty()) {
                validateGradeConflict(request.getAllowedGradeIds(), id);
            }
            validateFields(
                    request.getTopicStartTime() != null ? request.getTopicStartTime() : campaign.getTopicStartTime(),
                    request.getTopicEndTime() != null ? request.getTopicEndTime() : campaign.getTopicEndTime(),
                    request.getSupervisorCapacity() != null ? request.getSupervisorCapacity() : campaign.getSupervisorCapacity(),
                    request.getFreeSelectCapacity() != null ? request.getFreeSelectCapacity() : campaign.getFreeSelectCapacity(),
                    request.getOpeningStartTime() != null ? request.getOpeningStartTime() : campaign.getOpeningStartTime(),
                    request.getOpeningEndTime() != null ? request.getOpeningEndTime() : campaign.getOpeningEndTime(),
                    request.getMidtermStartTime() != null ? request.getMidtermStartTime() : campaign.getMidtermStartTime(),
                    request.getMidtermEndTime() != null ? request.getMidtermEndTime() : campaign.getMidtermEndTime(),
                    request.getThesisStartTime() != null ? request.getThesisStartTime() : campaign.getThesisStartTime(),
                    request.getThesisEndTime() != null ? request.getThesisEndTime() : campaign.getThesisEndTime());
            applyUpdate(campaign, request);
        }
        updateById(campaign);
        logService.record(campaign.getId(), operatorUserId, operatorType, "更新毕设活动",
                "graduation_campaign", campaign.getId(), null);
        return toResponse(campaign);
    }

    @Override
    @Transactional
    public void changeCampaignStatus(Long operatorUserId, String operatorType, Long id, CampaignStatusEnum status) {
        if (status == null) {
            throw new BusinessException(400, "状态不能为空");
        }
        GraduationCampaign campaign = requireCampaign(id);
        if (status == CampaignStatusEnum.OPEN) {
            // 开放前校验配置完整（名称/时间窗/名额已由创建时保证，这里兜底）
            if (campaign.getTopicStartTime() == null || campaign.getTopicEndTime() == null
                    || campaign.getSupervisorCapacity() == null || campaign.getFreeSelectCapacity() == null) {
                throw new BusinessException(400, "活动配置不完整，无法开放");
            }
        }
        campaign.setStatus(status);
        updateById(campaign);
        logService.record(campaign.getId(), operatorUserId, operatorType, "变更活动状态",
                "graduation_campaign", campaign.getId(), "新状态: " + status.getDescription());
    }

    @Override
    public PageResult<CampaignResponse> listCampaigns(CampaignStatusEnum status, PageQuery pageQuery) {
        Page<GraduationCampaign> page = baseMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<GraduationCampaign>()
                        .eq(status != null, GraduationCampaign::getStatus, status)
                        .orderByDesc(GraduationCampaign::getId));
        return PageResult.of(page, page.getRecords().stream().map(this::toResponse).toList());
    }

    @Override
    public CampaignResponse getCampaign(Long id) {
        return toResponse(requireCampaign(id));
    }

    @Override
    public List<CampaignResponse> listAvailableCampaignsForStudent(Long studentUserId) {
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, studentUserId)
                .last("LIMIT 1"));
        if (student == null || student.getGradeId() == null) {
            return List.of();
        }
        List<GraduationCampaign> campaigns = baseMapper.selectList(
                new LambdaQueryWrapper<GraduationCampaign>()
                        .eq(GraduationCampaign::getStatus, CampaignStatusEnum.OPEN)
                        .orderByDesc(GraduationCampaign::getId));
        return campaigns.stream()
                .filter(c -> gradeIdsOf(c).contains(student.getGradeId()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<CampaignResponse> listCampaignsForSelector() {
        List<GraduationCampaign> campaigns = baseMapper.selectList(
                new LambdaQueryWrapper<GraduationCampaign>()
                        .ne(GraduationCampaign::getStatus, CampaignStatusEnum.DRAFT)
                        .orderByDesc(GraduationCampaign::getId));
        return campaigns.stream().map(this::toResponse).toList();
    }

    // ---- helpers ----

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = baseMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private void validateFields(LocalDateTime topicStart, LocalDateTime topicEnd,
                                Integer supervisorCapacity, Integer freeSelectCapacity,
                                LocalDateTime openingStart, LocalDateTime openingEnd,
                                LocalDateTime midtermStart, LocalDateTime midtermEnd,
                                LocalDateTime thesisStart, LocalDateTime thesisEnd) {
        ParamValidator.requireNonNull(topicStart, "选题开始时间");
        ParamValidator.requireNonNull(topicEnd, "选题截止时间");
        if (!topicEnd.isAfter(topicStart)) {
            throw new BusinessException(400, "选题截止时间必须晚于开始时间");
        }
        ParamValidator.requirePositive(supervisorCapacity, "教师可分配学生数上限");
        ParamValidator.requirePositive(freeSelectCapacity, "教师自由选择学生数上限");
        if (freeSelectCapacity > supervisorCapacity) {
            throw new BusinessException(400, "自由选择上限不能超过可分配上限");
        }
        validateWindow(openingStart, openingEnd, "开题报告");
        validateWindow(midtermStart, midtermEnd, "中期检查");
        validateWindow(thesisStart, thesisEnd, "论文提交");
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end, String name) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(400, name + "截止时间必须晚于开始时间");
        }
    }

    /** R-4.2：同一年级同一时期只允许一个「进行中」活动 */
    private void validateGradeConflict(List<Long> gradeIds, Long excludeCampaignId) {
        List<GraduationCampaign> openCampaigns = baseMapper.selectList(
                new LambdaQueryWrapper<GraduationCampaign>()
                        .eq(GraduationCampaign::getStatus, CampaignStatusEnum.OPEN)
                        .ne(excludeCampaignId != null, GraduationCampaign::getId, excludeCampaignId));
        Set<Long> incoming = new HashSet<>(gradeIds);
        for (GraduationCampaign c : openCampaigns) {
            Set<Long> existing = gradeIdsOf(c);
            existing.retainAll(incoming);
            if (!existing.isEmpty()) {
                throw new BusinessException(409, "同年级已存在进行中的活动，不可重复开展");
            }
        }
    }

    private Boolean sameGradeIds(List<Long> list, String stored) {
        Set<Long> a = new HashSet<>(list);
        Set<Long> b = gradeIdsOf(stored);
        return a.equals(b);
    }

    private Set<Long> gradeIdsOf(GraduationCampaign campaign) {
        return gradeIdsOf(campaign.getAllowedGradeIds());
    }

    private Set<Long> gradeIdsOf(String stored) {
        Set<Long> ids = new HashSet<>();
        if (stored == null || stored.isBlank()) {
            return ids;
        }
        for (String part : stored.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法段
            }
        }
        return ids;
    }

    private void applyUpdate(GraduationCampaign campaign, CampaignUpdateRequest request) {
        if (request.getName() != null) {
            campaign.setName(request.getName());
        }
        if (request.getAllowedGradeIds() != null && !request.getAllowedGradeIds().isEmpty()) {
            campaign.setAllowedGradeIds(String.join(",", request.getAllowedGradeIds().stream()
                    .map(String::valueOf).toList()));
        }
        if (request.getTopicStartTime() != null) {
            campaign.setTopicStartTime(request.getTopicStartTime());
        }
        if (request.getTopicEndTime() != null) {
            campaign.setTopicEndTime(request.getTopicEndTime());
        }
        if (request.getSupervisorCapacity() != null) {
            campaign.setSupervisorCapacity(request.getSupervisorCapacity());
        }
        if (request.getFreeSelectCapacity() != null) {
            campaign.setFreeSelectCapacity(request.getFreeSelectCapacity());
        }
        if (request.getOpeningStartTime() != null) {
            campaign.setOpeningStartTime(request.getOpeningStartTime());
        }
        if (request.getOpeningEndTime() != null) {
            campaign.setOpeningEndTime(request.getOpeningEndTime());
        }
        if (request.getMidtermStartTime() != null) {
            campaign.setMidtermStartTime(request.getMidtermStartTime());
        }
        if (request.getMidtermEndTime() != null) {
            campaign.setMidtermEndTime(request.getMidtermEndTime());
        }
        if (request.getThesisStartTime() != null) {
            campaign.setThesisStartTime(request.getThesisStartTime());
        }
        if (request.getThesisEndTime() != null) {
            campaign.setThesisEndTime(request.getThesisEndTime());
        }
        if (request.getAdvisorWeight() != null) {
            campaign.setAdvisorWeight(request.getAdvisorWeight());
        }
        if (request.getReviewerWeight() != null) {
            campaign.setReviewerWeight(request.getReviewerWeight());
        }
        if (request.getDefenseWeight() != null) {
            campaign.setDefenseWeight(request.getDefenseWeight());
        }
        if (campaign.getAdvisorWeight() + campaign.getReviewerWeight() + campaign.getDefenseWeight() != 100) {
            throw new BusinessException(400, "成绩权重之和必须为100");
        }
    }

    private CampaignResponse toResponse(GraduationCampaign campaign) {
        CampaignResponse resp = new CampaignResponse();
        resp.setId(campaign.getId());
        resp.setName(campaign.getName());
        resp.setAllowedGradeIds(gradeIdsOf(campaign).stream().sorted().toList());
        resp.setTopicStartTime(campaign.getTopicStartTime());
        resp.setTopicEndTime(campaign.getTopicEndTime());
        resp.setSupervisorCapacity(campaign.getSupervisorCapacity());
        resp.setFreeSelectCapacity(campaign.getFreeSelectCapacity());
        resp.setOpeningStartTime(campaign.getOpeningStartTime());
        resp.setOpeningEndTime(campaign.getOpeningEndTime());
        resp.setMidtermStartTime(campaign.getMidtermStartTime());
        resp.setMidtermEndTime(campaign.getMidtermEndTime());
        resp.setThesisStartTime(campaign.getThesisStartTime());
        resp.setThesisEndTime(campaign.getThesisEndTime());
        resp.setAdvisorWeight(campaign.getAdvisorWeight());
        resp.setReviewerWeight(campaign.getReviewerWeight());
        resp.setDefenseWeight(campaign.getDefenseWeight());
        resp.setStatus(campaign.getStatus());
        resp.setCreateTime(campaign.getCreateTime());
        return resp;
    }
}
