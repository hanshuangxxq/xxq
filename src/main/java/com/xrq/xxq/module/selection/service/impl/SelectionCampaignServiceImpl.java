package com.xrq.xxq.module.selection.service.impl;

import java.util.Arrays;
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

import java.time.format.DateTimeFormatter;

import com.xrq.xxq.module.notification.entity.NotificationTargetEnum;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import com.xrq.xxq.module.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelectionCampaignServiceImpl
        extends ServiceImpl<SelectionCampaignMapper, SelectionCampaign>
        implements SelectionCampaignService {

    private static final String SOURCE_SELECTION_CAMPAIGN = "SELECTION_CAMPAIGN";
    private static final int DEFAULT_START_WEEK = 1;
    private static final int DEFAULT_END_WEEK = 16;

    private final SelectionGroupMapper selectionGroupMapper;
    private final SelectionCampaignTimeRestrictionMapper selectionCampaignTimeRestrictionMapper;
    private final SelectionRecordMapper selectionRecordMapper;
    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final TimeRestrictionMapper timeRestrictionMapper;
    private final SemesterService semesterService;
    private final SelectionClassService selectionClassService;
    private final CourseMapper courseMapper;
    private final NotificationService notificationService;

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
        // 课程编号在学期内唯一
        Long codeConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionCampaign>()
                .eq(SelectionCampaign::getSemesterId, request.getSemesterId())
                .eq(SelectionCampaign::getCourseCode, request.getCourseCode()));
        if (codeConflict > 0) {
            throw new BusinessException(409, "该课程编号已在本学期中存在");
        }
        if (request.getGroupId() != null && selectionGroupMapper.selectById(request.getGroupId()) == null) {
            throw new BusinessException(404, "选课组不存在");
        }

        // 1. 在 course 表插入衍生记录（source = SELECTION_CAMPAIGN，courseName = 活动名）
        Course derivedCourse = new Course();
        derivedCourse.setCourseName(request.getName());
        if (request.getCourseCode() != null){
            derivedCourse.setCourseCode(request.getCourseCode());
        }else {
            derivedCourse.setCourseCode("SEL-CAMP-" + request.getSemesterId() + "-" + System.currentTimeMillis());
        }
        derivedCourse.setCredit(request.getCredit());
        derivedCourse.setCourseHour(request.getCourseHour());
        derivedCourse.setDescription(request.getDescription());
        derivedCourse.setCourseType(request.getCourseType());
        derivedCourse.setSource(SOURCE_SELECTION_CAMPAIGN);
        courseMapper.insert(derivedCourse);

        // 2. 创建 campaign（含课程字段 + groupId；name 即课程名）
        SelectionCampaign campaign = new SelectionCampaign();
        campaign.setName(request.getName());
        campaign.setSemesterId(request.getSemesterId());
        campaign.setCourseId(derivedCourse.getId());
        campaign.setStartWeek(request.getStartWeek() != null ? request.getStartWeek() : DEFAULT_START_WEEK);
        campaign.setEndWeek(request.getEndWeek() != null ? request.getEndWeek() : DEFAULT_END_WEEK);
        campaign.setStartTime(request.getStartTime());
        campaign.setEndTime(request.getEndTime());
        campaign.setStatus(CampaignStatusEnum.DRAFT);
        campaign.setCourseCode(request.getCourseCode());
        campaign.setCredit(request.getCredit());
        campaign.setCourseHour(request.getCourseHour());
        campaign.setDescription(request.getDescription());
        campaign.setCourseType(request.getCourseType());
        campaign.setAllowedGradeIds(joinLongs(request.getAllowedGradeIds()));
        campaign.setAllowedMajors(joinLongs(request.getAllowedMajors()));
        campaign.setCapacity(request.getCapacity());
        campaign.setGroupId(request.getGroupId());
        save(campaign);

        // 3. 绑定 TimeRestriction（RESERVED 类型，courseId 指向衍生 course）
        bindTimeRestrictions(campaign, request.getTimeRestrictionIds(), derivedCourse.getId());

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

        // 课程字段部分更新（name 即课程名，变更时同步衍生 course.courseName）
        boolean courseChanged = false;
        Course derived = courseMapper.selectById(campaign.getCourseId());
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BusinessException(400, "活动名称不能为空");
            }
            campaign.setName(request.getName());
            if (derived != null) derived.setCourseName(request.getName());
            courseChanged = true;
        }
        if (request.getCourseCode() != null) {
            Long codeConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionCampaign>()
                    .eq(SelectionCampaign::getSemesterId, campaign.getSemesterId())
                    .eq(SelectionCampaign::getCourseCode, request.getCourseCode())
                    .ne(SelectionCampaign::getId, id));
            if (codeConflict > 0) {
                throw new BusinessException(409, "该课程编号已在本学期中存在");
            }
            campaign.setCourseCode(request.getCourseCode());
            courseChanged = true;
        }
        if (request.getCredit() != null) { campaign.setCredit(request.getCredit()); if (derived != null) derived.setCredit(request.getCredit()); courseChanged = true; }
        if (request.getCourseHour() != null) { campaign.setCourseHour(request.getCourseHour()); if (derived != null) derived.setCourseHour(request.getCourseHour()); courseChanged = true; }
        if (request.getDescription() != null) { campaign.setDescription(request.getDescription()); if (derived != null) derived.setDescription(request.getDescription()); courseChanged = true; }
        if (request.getCourseType() != null) { campaign.setCourseType(request.getCourseType()); if (derived != null) derived.setCourseType(request.getCourseType()); courseChanged = true; }
        if (request.getAllowedGradeIds() != null) campaign.setAllowedGradeIds(joinLongs(request.getAllowedGradeIds()));
        if (request.getAllowedMajors() != null) campaign.setAllowedMajors(joinLongs(request.getAllowedMajors()));
        if (request.getCapacity() != null) {
            if (request.getCapacity() <= 0) {
                throw new BusinessException(400, "容量必须大于0");
            }
            campaign.setCapacity(request.getCapacity());
        }

        updateById(campaign);
        if (courseChanged && derived != null) {
            courseMapper.updateById(derived);
        }

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
        Map<Long, String> groupNameMap = loadGroupNameMap(list);
        return list.stream()
                .map(c -> toResponse(c, semesterService.getById(c.getSemesterId()), groupNameMap))
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
        if (courseId == null) {
            return;
        }
        SelectionCampaign campaign = baseMapper.selectOne(
                new LambdaQueryWrapper<SelectionCampaign>()
                        .eq(SelectionCampaign::getCourseId, courseId));
        if (campaign == null) {
            return;
        }
        doCascadeDelete(campaign, false);
    }

    /**
     * 级联清理：选课班成员 -> 选课班 -> 选课记录 -> 时段限制关联 -> 时段限制(RESERVED) -> 活动（-> 衍生 course）。
     *
     * @param deleteCourse 是否删除衍生 Course；delete() 传 true，deleteByCourseId() 传 false（由调用方删 Course）
     */
    private void doCascadeDelete(SelectionCampaign campaign, boolean deleteCourse) {
        Long id = campaign.getId();
        List<SelectionClass> classes = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getCampaignId, id));
        if (!classes.isEmpty()) {
            List<Long> classIds = classes.stream().map(SelectionClass::getId).toList();
            selectionClassMemberMapper.delete(new LambdaQueryWrapper<SelectionClassMember>()
                    .in(SelectionClassMember::getClassId, classIds));
            selectionClassMapper.delete(new LambdaQueryWrapper<SelectionClass>()
                    .eq(SelectionClass::getCampaignId, id));
        }
        selectionRecordMapper.delete(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, id));
        selectionCampaignTimeRestrictionMapper.delete(new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                .eq(SelectionCampaignTimeRestriction::getCampaignId, id));
        if (campaign.getCourseId() != null) {
            timeRestrictionMapper.delete(new LambdaQueryWrapper<TimeRestriction>()
                    .eq(TimeRestriction::getCourseId, campaign.getCourseId()));
        }
        Long campaignCourseId = campaign.getCourseId();
        removeById(id);
        if (deleteCourse && campaignCourseId != null) {
            courseMapper.deleteById(campaignCourseId);
        }
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
        validateCourseFields(campaign.getName(), campaign.getCourseCode(),
                campaign.getCredit(), campaign.getCapacity());
        campaign.setStatus(CampaignStatusEnum.OPEN);
        updateById(campaign);

        // 全局通知所有学生：广播仅落库 1 条，在线学生实时推送，离线学生上线补推
        try {
            String endTimeText = campaign.getEndTime() != null
                    ? campaign.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "详见系统";
            notificationService.broadcast(
                    NotificationTypeEnum.SELECTION,
                    NotificationTargetEnum.STUDENT,
                    "选课开始通知",
                    "选课活动《" + campaign.getName() + "》已开放，截止时间 " + endTimeText
                            + "，请及时登录系统完成选课。",
                    senderId);
        } catch (Exception e) {
            // 通知失败不影响开课主流程
            log.warn("选课开始广播通知失败: campaignId={}", id, e);
        }
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
     * 绑定 TimeRestriction（RESERVED 类型，courseId 指向衍生 course）。
     * 仅在课程刚创建、衍生 course 刚插入后调用。
     */
    private void bindTimeRestrictions(SelectionCampaign campaign, List<Long> trIds, Long derivedCourseId) {
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
            if (tr.getCourseId() != null && !tr.getCourseId().equals(derivedCourseId)) {
                throw new BusinessException(400, "时段限制 " + trId + " 已预留给其他课程");
            }
            if (tr.getCourseId() == null) {
                tr.setCourseId(derivedCourseId);
                timeRestrictionMapper.updateById(tr);
            }
            SelectionCampaignTimeRestriction rel = new SelectionCampaignTimeRestriction();
            rel.setCampaignId(campaign.getId());
            rel.setTimeRestrictionId(trId);
            selectionCampaignTimeRestrictionMapper.insert(rel);
        }
    }

    /**
     * 重绑 TimeRestriction：先清理旧关联（同时删除指向衍生 course 的 RESERVED 时段限制），
     * 再按新列表重新绑定。要求 DRAFT 状态。
     */
    private void rebindTimeRestrictions(SelectionCampaign campaign, List<Long> newTrIds) {
        selectionCampaignTimeRestrictionMapper.delete(new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                .eq(SelectionCampaignTimeRestriction::getCampaignId, campaign.getId()));
        if (campaign.getCourseId() != null) {
            timeRestrictionMapper.delete(new LambdaQueryWrapper<TimeRestriction>()
                    .eq(TimeRestriction::getCourseId, campaign.getCourseId()));
        }
        bindTimeRestrictions(campaign, newTrIds, campaign.getCourseId());
    }

    private void validateCourseFields(String name, String courseCode, Integer credit, Integer capacity) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "活动名称不能为空");
        }
        if (courseCode == null || courseCode.isBlank()) {
            throw new BusinessException(400, "课程编号不能为空");
        }
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
        return toResponse(campaign, semester, groupNameMap);
    }

    private CampaignResponse toResponse(SelectionCampaign campaign, Semester semester, Map<Long, String> groupNameMap) {
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
        resp.setGroupName(groupNameMap != null ? groupNameMap.get(campaign.getGroupId()) : null);
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
