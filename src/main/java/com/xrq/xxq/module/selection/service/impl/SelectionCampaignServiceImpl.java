package com.xrq.xxq.module.selection.service.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import com.xrq.xxq.module.selection.entity.SelectionCampaignTimeRestriction;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignTimeRestrictionMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionClassService;
import com.xrq.xxq.module.time.entity.TimeRestriction;
import com.xrq.xxq.module.time.mapper.TimeRestrictionMapper;
import com.xrq.xxq.util.ParamValidator;

import java.time.format.DateTimeFormatter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.xrq.xxq.common.event.CampaignOpenedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelectionCampaignServiceImpl
        extends ServiceImpl<SelectionCampaignMapper, SelectionCampaign>
        implements SelectionCampaignService {

    private static final int DEFAULT_START_WEEK = 1;
    private static final int DEFAULT_END_WEEK = 16;

    private final SelectionGroupMapper selectionGroupMapper;
    private final SelectionCampaignTimeRestrictionMapper selectionCampaignTimeRestrictionMapper;
    private final TimeRestrictionMapper timeRestrictionMapper;
    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final SelectionRecordMapper selectionRecordMapper;
    private final SemesterService semesterService;
    private final SelectionClassService selectionClassService;
    private final CourseMapper courseMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

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
        validateCourseFields(request.getName(), request.getCourseCode(),
                request.getCredit(), request.getCapacity());
        // 课程编号在学期内唯一：公选课活动间 + 常规 course 表均不可冲突
        Long codeConflict = baseMapper.countByCourseCodeInSemester(
                request.getSemesterId(), request.getCourseCode(), null);
        if (codeConflict != null && codeConflict > 0) {
            throw new BusinessException(409, "该课程编号已在本学期中存在");
        }
        if (courseMapper.exists(new LambdaQueryWrapper<Course>()
                .eq(Course::getCourseCode, request.getCourseCode()))) {
            throw new BusinessException(409, "该课程编号已存在");
        }
        if (request.getGroupId() != null && selectionGroupMapper.selectById(request.getGroupId()) == null) {
            throw new BusinessException(404, "选课组不存在");
        }

        // 公选课课程信息直接存入 campaign，不再在 course 表生成衍生记录
        SelectionCampaign campaign = new SelectionCampaign();
        campaign.setSemesterId(request.getSemesterId());
        campaign.setCourseName(request.getName());
        campaign.setCourseCode(request.getCourseCode() != null
                ? request.getCourseCode()
                : "SEL-CAMP-" + request.getSemesterId() + "-" + System.currentTimeMillis());
        campaign.setCredit(request.getCredit());
        campaign.setCourseHour(request.getCourseHour());
        campaign.setDescription(request.getDescription());
        campaign.setCourseType(request.getCourseType());
        campaign.setStartWeek(request.getStartWeek() != null ? request.getStartWeek() : DEFAULT_START_WEEK);
        campaign.setEndWeek(request.getEndWeek() != null ? request.getEndWeek() : DEFAULT_END_WEEK);
        campaign.setStartTime(request.getStartTime());
        campaign.setEndTime(request.getEndTime());
        campaign.setStatus(CampaignStatusEnum.DRAFT);
        campaign.setAllowedGradeIds(joinLongs(request.getAllowedGradeIds()));
        campaign.setAllowedMajors(joinLongs(request.getAllowedMajors()));
        campaign.setCapacity(request.getCapacity());
        campaign.setGroupId(request.getGroupId());
        save(campaign);

        // 绑定 TimeRestriction（RESERVED 类型，campaignId 指向本活动）
        bindTimeRestrictions(campaign, request.getTimeRestrictionIds());

        return toResponse(campaign, semester, true);
    }

    @Override
    @Transactional
    public CampaignResponse update(Long id, CampaignUpdateRequest request) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可修改");
        }
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

        // 课程字段更新直接写 campaign（不再走 course 表）
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BusinessException(400, "活动名称不能为空");
            }
            campaign.setCourseName(request.getName());
        }
        if (request.getCourseCode() != null) {
            Long codeConflict = baseMapper.countByCourseCodeInSemester(
                    campaign.getSemesterId(), request.getCourseCode(), id);
            if (codeConflict != null && codeConflict > 0) {
                throw new BusinessException(409, "该课程编号已在本学期中存在");
            }
            if (courseMapper.exists(new LambdaQueryWrapper<Course>()
                    .eq(Course::getCourseCode, request.getCourseCode()))) {
                throw new BusinessException(409, "该课程编号已存在");
            }
            campaign.setCourseCode(request.getCourseCode());
        }
        if (request.getCredit() != null) campaign.setCredit(request.getCredit());
        if (request.getCourseHour() != null) campaign.setCourseHour(request.getCourseHour());
        if (request.getDescription() != null) campaign.setDescription(request.getDescription());
        if (request.getCourseType() != null) campaign.setCourseType(request.getCourseType());
        if (request.getAllowedGradeIds() != null) campaign.setAllowedGradeIds(joinLongs(request.getAllowedGradeIds()));
        if (request.getAllowedMajors() != null) campaign.setAllowedMajors(joinLongs(request.getAllowedMajors()));
        if (request.getCapacity() != null) {
            if (request.getCapacity() <= 0) {
                throw new BusinessException(400, "容量必须大于0");
            }
            campaign.setCapacity(request.getCapacity());
        }

        updateById(campaign);

        // 时段限制重绑（仅当请求显式传入时）
        if (request.getTimeRestrictionIds() != null) {
            rebindTimeRestrictions(campaign, request.getTimeRestrictionIds());
        }

        // 选课组换绑 / 解绑
        if (request.getGroupId() != null) {
            if (Boolean.TRUE.equals(request.getUnbindGroup())) {
                throw new BusinessException(400, "groupId 与 unbindGroup=true 不可同时传入");
            }
            bindGroup(campaign, request.getGroupId());
        } else if (Boolean.TRUE.equals(request.getUnbindGroup())) {
            unbindGroup(campaign);
        }
        return toResponse(campaign, semesterService.getById(campaign.getSemesterId()), true);
    }

    @Override
    public CampaignResponse getDetail(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        return toResponse(campaign, semesterService.getById(campaign.getSemesterId()), true);
    }

    @Override
    public List<CampaignResponse> listAll() {
        List<SelectionCampaign> list = list();
        if (list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .map(c -> toResponse(c, semesterService.getById(c.getSemesterId()), true))
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
        // 过滤规则：group_id 为 NULL（未绑定）或等于当前 groupId（已绑定到本组）
        return allCampaigns.stream()
                .filter(c -> c.getGroupId() == null || groupId.equals(c.getGroupId()))
                .map(c -> {
                    CampaignResponse resp = toResponse(c, semesterService.getById(c.getSemesterId()), true);
                    if (groupId.equals(c.getGroupId())) {
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
        doCascadeDelete(campaign, true);
    }

    @Override
    @Transactional
    public void deleteByCourseId(Long courseId) {
        // 公选课不再在 course 表生成衍生记录，删除 course 无需联动清理选课活动。
        // 常规课与选课活动已无 course_id 关联，静默返回。
        if (courseId == null) {
            return;
        }
    }

    /**
     * 级联清理：DB 的 ON DELETE CASCADE 已移除，应用层显式按依赖顺序删子表。
     * <p>
     * 顺序：selection_class_member（按本活动下所有 selection_class.id 批量删）
     * -> selection_class（by campaign_id）-> selection_record（by campaign_id）
     * -> selection_campaign_time_restriction（by campaign_id）
     * -> time_restriction（campaign_id 指向本活动的 RESERVED 时段）
     * -> Redis 选课计数器 -> 活动主表。
     *
     * @param deleteCourse 是否删除衍生 Course；公选课无衍生 course，此参数保留以兼容签名
     */
    private void doCascadeDelete(SelectionCampaign campaign, boolean deleteCourse) {
        Long campaignId = campaign.getId();
        // 1. 删 selection_class_member：先取本活动下所有 selection_class.id，再批量删其成员
        List<Long> classIds = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>()
                        .eq(SelectionClass::getCampaignId, campaignId))
                .stream().map(SelectionClass::getId).toList();
        if (!classIds.isEmpty()) {
            selectionClassMemberMapper.delete(new LambdaQueryWrapper<SelectionClassMember>()
                    .in(SelectionClassMember::getClassId, classIds));
        }
        // 2. 删 selection_class（by campaign_id）
        selectionClassMapper.delete(new LambdaQueryWrapper<SelectionClass>()
                .eq(SelectionClass::getCampaignId, campaignId));
        // 3. 删 selection_record（by campaign_id）
        selectionRecordMapper.delete(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, campaignId));
        // 4. 删 selection_campaign_time_restriction（by campaign_id）
        selectionCampaignTimeRestrictionMapper.delete(new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                .eq(SelectionCampaignTimeRestriction::getCampaignId, campaignId));
        // 5. 删公选课的 RESERVED 时段限制（campaign_id 指向本活动）
        timeRestrictionMapper.delete(new LambdaQueryWrapper<TimeRestriction>()
                .eq(TimeRestriction::getCampaignId, campaignId));
        // 6. 清理 Redis 选课计数器
        redisTemplate.delete("selection:count:" + campaignId);
        // 7. 删活动主表
        removeById(campaignId);
        // 公选课无衍生 course 记录，无需删除 course 表
    }

    @Override
    public void open(Long id, Long senderId) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可开启");
        }
        validateCourseFields(campaign.getCourseName(),
                campaign.getCourseCode(),
                campaign.getCredit(),
                campaign.getCapacity());
        campaign.setStatus(CampaignStatusEnum.OPEN);
        updateById(campaign);

        // 发布活动开放事件，由通知监听器 AFTER_COMMIT 异步广播（业务与通知解耦）
        String endTimeText = campaign.getEndTime() != null
                ? campaign.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "详见系统";
        String courseName = campaign.getCourseName() != null ? campaign.getCourseName() : "未知活动";
        eventPublisher.publishEvent(new CampaignOpenedEvent(courseName, endTimeText, senderId));
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

    /**
     * 将活动绑定到指定选课组（直接改 campaign.group_id）。
     * <p>
     * 约束：一个活动只能绑定一个选课组。若已绑定到同组，视为幂等成功；若已绑定到其它组，直接覆盖。
     */
    private void bindGroup(SelectionCampaign campaign, Long groupId) {
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可配置选课组");
        }
        if (selectionGroupMapper.selectById(groupId) == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        campaign.setGroupId(groupId);
        updateById(campaign);
    }

    /**
     * 解绑活动当前绑定的选课组（campaign.group_id 置 NULL）。
     */
    private void unbindGroup(SelectionCampaign campaign) {
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可解绑选课组");
        }
        if (campaign.getGroupId() == null) {
            return;
        }
        campaign.setGroupId(null);
        updateById(campaign);
    }

    /**
     * 绑定 TimeRestriction（RESERVED 类型，campaignId 指向本活动）。
     */
    private void bindTimeRestrictions(SelectionCampaign campaign, List<Long> trIds) {
        if (trIds == null || trIds.isEmpty()) {
            return;
        }
        for (Long trId : trIds) {
            TimeRestriction tr = timeRestrictionMapper.selectById(trId);
            if (tr == null) {
                throw new BusinessException(400, "时段限制 " + trId + " 不存在");
            }
            if (!"RESERVED".equals(tr.getRestrictionType())) {
                throw new BusinessException(400, "时段限制 " + trId + " 必须为 RESERVED 类型");
            }
            if (tr.getCourseId() != null
                    || (tr.getCampaignId() != null && !tr.getCampaignId().equals(campaign.getId()))) {
                throw new BusinessException(400, "时段限制 " + trId + " 已预留给其他课程");
            }
            if (tr.getCampaignId() == null) {
                tr.setCampaignId(campaign.getId());
                timeRestrictionMapper.updateById(tr);
            }
            SelectionCampaignTimeRestriction rel = new SelectionCampaignTimeRestriction();
            rel.setCampaignId(campaign.getId());
            rel.setTimeRestrictionId(trId);
            selectionCampaignTimeRestrictionMapper.insert(rel);
        }
    }

    /**
     * 重绑 TimeRestriction：增量 diff（按 timeRestrictionId 差集），仅增删变化部分，保留项不动。
     * 移除的：删关联 + 删指向衍生 course 的 RESERVED 时段；新增的：认领时段 + 插关联。要求 DRAFT 状态。
     */
    private void rebindTimeRestrictions(SelectionCampaign campaign, List<Long> newTrIds) {
        List<SelectionCampaignTimeRestriction> existing = selectionCampaignTimeRestrictionMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                        .eq(SelectionCampaignTimeRestriction::getCampaignId, campaign.getId()));
        Set<Long> existingIds = existing.stream()
                .map(SelectionCampaignTimeRestriction::getTimeRestrictionId)
                .collect(Collectors.toSet());
        Set<Long> newIds = newTrIds != null ? new HashSet<>(newTrIds) : new HashSet<>();

        // 移除：现有 - 新 -> 删关联 + 删指向本活动的 RESERVED 时段
        Set<Long> toRemove = new HashSet<>(existingIds);
        toRemove.removeAll(newIds);
        for (Long trId : toRemove) {
            selectionCampaignTimeRestrictionMapper.delete(new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                    .eq(SelectionCampaignTimeRestriction::getCampaignId, campaign.getId())
                    .eq(SelectionCampaignTimeRestriction::getTimeRestrictionId, trId));
            timeRestrictionMapper.delete(new LambdaQueryWrapper<TimeRestriction>()
                    .eq(TimeRestriction::getId, trId)
                    .eq(TimeRestriction::getCampaignId, campaign.getId()));
        }

        // 新增：新 - 现有 -> 认领时段 + 插关联
        Set<Long> toAdd = new HashSet<>(newIds);
        toAdd.removeAll(existingIds);
        for (Long trId : toAdd) {
            TimeRestriction tr = timeRestrictionMapper.selectById(trId);
            if (tr == null) {
                throw new BusinessException(400, "时段限制 " + trId + " 不存在");
            }
            if (!"RESERVED".equals(tr.getRestrictionType())) {
                throw new BusinessException(400, "时段限制 " + trId + " 必须为 RESERVED 类型");
            }
            if (tr.getCourseId() != null
                    || (tr.getCampaignId() != null && !tr.getCampaignId().equals(campaign.getId()))) {
                throw new BusinessException(400, "时段限制 " + trId + " 已预留给其他课程");
            }
            if (tr.getCampaignId() == null) {
                tr.setCampaignId(campaign.getId());
                timeRestrictionMapper.updateById(tr);
            }
            SelectionCampaignTimeRestriction rel = new SelectionCampaignTimeRestriction();
            rel.setCampaignId(campaign.getId());
            rel.setTimeRestrictionId(trId);
            selectionCampaignTimeRestrictionMapper.insert(rel);
        }
    }

    private void validateCourseFields(String name, String courseCode, Integer credit, Integer capacity) {
        ParamValidator.requireNonBlank(name, "活动名称");
        ParamValidator.requireNonBlank(courseCode, "课程编号");
        if (credit == null || credit < 0) {
            throw new BusinessException(400, "学分必须 >= 0");
        }
        if (capacity == null || capacity <= 0) {
            throw new BusinessException(400, "容量必须大于0");
        }
    }

    private Map<Long, String> loadGroupNameMap(List<SelectionCampaign> campaigns) {
        List<Long> ids = campaigns.stream()
                .map(SelectionCampaign::getGroupId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return selectionGroupMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(SelectionGroup::getId, SelectionGroup::getName));
    }

    private CampaignResponse toResponse(SelectionCampaign campaign, Semester semester, boolean loadGroup) {
        Map<Long, String> groupNameMap = loadGroup && campaign.getGroupId() != null
                ? selectionGroupMapper.selectByIds(List.of(campaign.getGroupId())).stream()
                        .collect(Collectors.toMap(SelectionGroup::getId, SelectionGroup::getName))
                : Map.of();
        CampaignResponse resp = new CampaignResponse();
        resp.setId(campaign.getId());
        resp.setName(campaign.getCourseName());
        resp.setSemesterId(campaign.getSemesterId());
        resp.setSemesterName(semester != null ? semester.getName() : null);
        resp.setCourseId(null); // 公选课不再关联 course 表
        resp.setStartWeek(campaign.getStartWeek());
        resp.setEndWeek(campaign.getEndWeek());
        resp.setStartTime(campaign.getStartTime());
        resp.setEndTime(campaign.getEndTime());
        resp.setStatus(campaign.getStatus());
        resp.setCourseCode(campaign.getCourseCode());
        resp.setCredit(campaign.getCredit());
        resp.setCourseHour(campaign.getCourseHour());
        resp.setDescription(campaign.getDescription());
        resp.setCourseType(campaign.getCourseType() != null ? campaign.getCourseType().getDescription() : null);
        resp.setAllowedGradeIds(parseLongs(campaign.getAllowedGradeIds()));
        resp.setAllowedMajors(parseLongs(campaign.getAllowedMajors()));
        List<SelectionCampaignTimeRestriction> rels = selectionCampaignTimeRestrictionMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                        .eq(SelectionCampaignTimeRestriction::getCampaignId, campaign.getId()));
        resp.setTimeRestrictionIds(rels.stream().map(SelectionCampaignTimeRestriction::getTimeRestrictionId).toList());
        resp.setCapacity(campaign.getCapacity());
        resp.setGroupId(campaign.getGroupId());
        // groupId 为 null（未绑定选课组）时 groupNameMap 是 Map.of()，不可变 Map 的 get(null) 会抛 NPE，
        // 故先判 groupId 非空再查；groupNameMap 为 null（防御）或查不到时回退 null。
        resp.setGroupName(campaign.getGroupId() != null && groupNameMap != null
                ? groupNameMap.get(campaign.getGroupId()) : null);
        resp.setCreateTime(campaign.getCreateTime());
        return resp;
    }

    private String joinLongs(List<Long> vals) {
        if (vals == null || vals.isEmpty()) {
            return null;
        }
        String joined = vals.stream().filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private List<Long> parseLongs(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
