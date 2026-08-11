package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.graduation.dto.AllocationRequest;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentOverviewRow;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentResponse;
import com.xrq.xxq.module.practice.graduation.dto.DashboardRow;
import com.xrq.xxq.module.practice.graduation.dto.PickRequest;
import com.xrq.xxq.module.practice.graduation.dto.ReassignRequest;
import com.xrq.xxq.module.practice.graduation.dto.TeacherPickPoolRow;
import com.xrq.xxq.module.practice.graduation.entity.AssignmentSourceEnum;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.GraduationAssignment;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationAssignmentMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationAssignmentService;
import com.xrq.xxq.module.practice.graduation.service.GraduationDashboardQuery;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.DistributedLock;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationAssignmentServiceImpl
        extends ServiceImpl<GraduationAssignmentMapper, GraduationAssignment>
        implements GraduationAssignmentService {

    private final GraduationCampaignMapper campaignMapper;
    private final GraduationDashboardQuery dashboardQuery;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final StudentScopeResolver scopeResolver;
    private final DistributedLock distributedLock;
    private final GraduationLogService logService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AssignmentResponse pickStudent(Long teacherUserId, PickRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放");
        }
        Long teacherCollegeId = scopeResolver.teacherCollegeId(teacherUserId);
        if (teacherCollegeId == null) {
            throw new BusinessException(403, "权限不足");
        }
        Long studentCollegeId = scopeResolver.studentCollegeId(request.getStudentId());
        if (!teacherCollegeId.equals(studentCollegeId)) {
            throw new BusinessException(403, "只能选择本院系学生");
        }
        // R-6.3：自由选择数 < 自由选择上限
        long picked = countTeacherAssignments(request.getCampaignId(), teacherUserId, AssignmentSourceEnum.TEACHER_PICK);
        if (picked >= campaign.getFreeSelectCapacity()) {
            throw new BusinessException(409, "自由选择名额已达上限");
        }
        // R-6.4：先占先得 —— 学生维度锁保护「查重 + 插入」
        return distributedLock.withLock("grad:assign:" + campaign.getId() + ":" + request.getStudentId(), 30, () -> {
            ensureStudentUnassigned(campaign.getId(), request.getStudentId());
            GraduationAssignment assignment = new GraduationAssignment();
            assignment.setCampaignId(campaign.getId());
            assignment.setStudentId(request.getStudentId());
            assignment.setTeacherId(teacherUserId);
            assignment.setSource(AssignmentSourceEnum.TEACHER_PICK);
            assignment.setAssignTime(LocalDateTime.now());
            baseMapper.insert(assignment);
            String title = "毕业选题匹配结果";
            String content = "教师已选择你作为指导对象，指导教师：" + nameOf(teacherUserId) + "。";
            eventPublisher.publishEvent(new PracticeNoticeEvent(request.getStudentId(), title, content));
            logService.record(campaign.getId(), teacherUserId, "teacher", "教师自选学生",
                    "graduation_assignment", assignment.getId(), "学生: " + request.getStudentId());
            return toResponse(assignment);
        });
    }

    @Override
    @Transactional
    public void cancelPick(Long teacherUserId, Long assignmentId) {
        GraduationAssignment assignment = baseMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "匹配记录不存在");
        }
        if (!assignment.getTeacherId().equals(teacherUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (assignment.getSource() != AssignmentSourceEnum.TEACHER_PICK) {
            throw new BusinessException(409, "仅可放弃自由选择的学生");
        }
        GraduationCampaign campaign = requireCampaign(assignment.getCampaignId());
        // R-6.6：选题截止前可撤销，截止后由院系改派处理
        if (LocalDateTime.now().isAfter(campaign.getTopicEndTime())) {
            throw new BusinessException(409, "选题已截止，撤销请通过院系管理者改派");
        }
        baseMapper.deleteById(assignmentId);
        logService.record(assignment.getCampaignId(), teacherUserId, "teacher", "教师放弃自选",
                "graduation_assignment", assignmentId, "学生: " + assignment.getStudentId());
    }

    @Override
    @Transactional
    public AssignmentResponse allocateStudent(Long deptUserId, AllocationRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        ParamValidator.requireNonNull(request.getTeacherId(), "教师");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放");
        }
        // R-6.8：院系指定分配在选题截止后开放
        if (!LocalDateTime.now().isAfter(campaign.getTopicEndTime())) {
            throw new BusinessException(409, "选题截止后才能进行指定分配");
        }
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        if (deptCollegeId == null) {
            throw new BusinessException(403, "权限不足");
        }
        // 学生与教师都必须属于本院系
        if (scopeResolver.departmentOwnsStudent(deptUserId, request.getStudentId())) {
            throw new BusinessException(403, "只能分配本院系学生");
        }
        Long teacherCollegeId = scopeResolver.teacherCollegeId(request.getTeacherId());
        if (!deptCollegeId.equals(teacherCollegeId)) {
            throw new BusinessException(403, "只能分配给本院系教师");
        }
        // R-6.10：教师名下总数 < 可分配上限
        long total = countTeacherAssignments(request.getCampaignId(), request.getTeacherId(), null);
        if (total >= campaign.getSupervisorCapacity()) {
            throw new BusinessException(409, "该教师名额已满");
        }
        return distributedLock.withLock("grad:assign:" + campaign.getId() + ":" + request.getStudentId(), 30, () -> {
            ensureStudentUnassigned(campaign.getId(), request.getStudentId());
            GraduationAssignment assignment = new GraduationAssignment();
            assignment.setCampaignId(campaign.getId());
            assignment.setStudentId(request.getStudentId());
            assignment.setTeacherId(request.getTeacherId());
            assignment.setSource(AssignmentSourceEnum.DEPT_ALLOCATE);
            assignment.setAssignTime(LocalDateTime.now());
            baseMapper.insert(assignment);
            String title = "毕业选题匹配结果";
            String content = "院系已为你指定指导教师：" + nameOf(request.getTeacherId()) + "。";
            eventPublisher.publishEvent(new PracticeNoticeEvent(request.getStudentId(), title, content));
            logService.record(campaign.getId(), deptUserId, "department", "院系指定分配",
                    "graduation_assignment", assignment.getId(),
                    "学生: " + request.getStudentId() + ", 教师: " + request.getTeacherId());
            return toResponse(assignment);
        });
    }

    @Override
    @Transactional
    public AssignmentResponse reassignStudent(Long deptUserId, ReassignRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        ParamValidator.requireNonNull(request.getNewTeacherId(), "新教师");
        ParamValidator.requireNonBlank(request.getReason(), "改派原因");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        // R-6.13：选题截止后由院系管理者改派
        if (!LocalDateTime.now().isAfter(campaign.getTopicEndTime())) {
            throw new BusinessException(409, "选题截止后才能执行改派");
        }
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        if (deptCollegeId == null) {
            throw new BusinessException(403, "权限不足");
        }
        if (scopeResolver.departmentOwnsStudent(deptUserId, request.getStudentId())) {
            throw new BusinessException(403, "只能改派本院系学生");
        }
        Long newTeacherCollegeId = scopeResolver.teacherCollegeId(request.getNewTeacherId());
        if (!deptCollegeId.equals(newTeacherCollegeId)) {
            throw new BusinessException(403, "只能改派给本院系教师");
        }
        return distributedLock.withLock("grad:assign:" + campaign.getId() + ":" + request.getStudentId(), 30, () -> {
            GraduationAssignment assignment = baseMapper.selectOne(new LambdaQueryWrapper<GraduationAssignment>()
                    .eq(GraduationAssignment::getCampaignId, campaign.getId())
                    .eq(GraduationAssignment::getStudentId, request.getStudentId())
                    .last("LIMIT 1"));
            if (assignment == null) {
                throw new BusinessException(404, "该学生尚无指导教师");
            }
            if (assignment.getTeacherId().equals(request.getNewTeacherId())) {
                throw new BusinessException(400, "新教师与原教师相同");
            }
            long newTotal = countTeacherAssignments(campaign.getId(), request.getNewTeacherId(), null);
            if (newTotal >= campaign.getSupervisorCapacity()) {
                throw new BusinessException(409, "新教师名额已满");
            }
            // 改派留痕（R-6.13：原教师/新教师/原因/操作人/时间）
            assignment.setPrevTeacherId(assignment.getTeacherId());
            assignment.setTeacherId(request.getNewTeacherId());
            assignment.setReassignReason(request.getReason());
            assignment.setReassignBy(deptUserId);
            assignment.setReassignTime(LocalDateTime.now());
            baseMapper.updateById(assignment);
            String title = "毕业选题改派通知";
            String content = "你的指导教师已调整为：" + nameOf(request.getNewTeacherId())
                    + "，原因：" + request.getReason();
            eventPublisher.publishEvent(new PracticeNoticeEvent(request.getStudentId(), title, content));
            logService.record(campaign.getId(), deptUserId, "department", "改派学生",
                    "graduation_assignment", assignment.getId(),
                    "学生: " + request.getStudentId() + ", 原教师: " + assignment.getPrevTeacherId()
                            + ", 新教师: " + request.getNewTeacherId() + ", 原因: " + request.getReason());
            return toResponse(assignment);
        });
    }

    @Override
    public List<TeacherPickPoolRow> listTeacherPickPool(Long teacherUserId, Long campaignId) {
        ParamValidator.requireNonNull(campaignId, "活动");
        GraduationCampaign campaign = requireCampaign(campaignId);
        Long teacherCollegeId = scopeResolver.teacherCollegeId(teacherUserId);
        List<DashboardRow> rows = dashboardQuery.queryRows(
                campaign.getId(), gradeIdsOf(campaign), teacherCollegeId, null, null);
        return rows.stream().map(r -> {
            TeacherPickPoolRow row = new TeacherPickPoolRow();
            row.setStudentId(r.getStudentId());
            row.setStudentNo(r.getStudentNo());
            row.setStudentName(r.getStudentName());
            row.setClassName(r.getClassName());
            row.setProposalTitle(r.getProposalTitle());
            row.setProposalContent(r.getProposalContent());
            row.setProposalStatus(r.getProposalStatus());
            row.setAssigned(r.getAssignmentId() != null);
            row.setAssignmentSource(r.getAssignmentSource());
            return row;
        }).toList();
    }

    @Override
    public List<AssignmentResponse> listMyAssignments(Long userId, String userType, Long campaignId) {
        LambdaQueryWrapper<GraduationAssignment> wrapper = new LambdaQueryWrapper<GraduationAssignment>()
                .eq(campaignId != null, GraduationAssignment::getCampaignId, campaignId)
                .orderByDesc(GraduationAssignment::getAssignTime);
        if ("student".equals(userType)) {
            wrapper.eq(GraduationAssignment::getStudentId, userId);
        } else if ("teacher".equals(userType)) {
            wrapper.eq(GraduationAssignment::getTeacherId, userId);
        }
        List<GraduationAssignment> list = baseMapper.selectList(wrapper);
        return toResponses(list);
    }

    @Override
    public List<AssignmentOverviewRow> listAssignmentOverview(Long academicAdminUserId, Long campaignId) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        List<Teacher> teachers = teacherMapper.selectList(null);
        Map<Long, List<GraduationAssignment>> byTeacher = baseMapper
                .selectList(new LambdaQueryWrapper<GraduationAssignment>()
                        .eq(GraduationAssignment::getCampaignId, campaignId))
                .stream().collect(Collectors.groupingBy(GraduationAssignment::getTeacherId));
        Map<Long, String> teacherNameMap = userMapper.toNameMap(
                teachers.stream().map(Teacher::getUserId).toList());
        List<AssignmentOverviewRow> rows = new ArrayList<>();
        for (Teacher teacher : teachers) {
            List<GraduationAssignment> mine = byTeacher.getOrDefault(teacher.getUserId(), List.of());
            long picked = mine.stream()
                    .filter(a -> a.getSource() == AssignmentSourceEnum.TEACHER_PICK).count();
            long allocated = mine.size() - picked;
            AssignmentOverviewRow row = new AssignmentOverviewRow();
            row.setTeacherId(teacher.getUserId());
            row.setTeacherName(teacherNameMap.get(teacher.getUserId()));
            row.setTeacherNo(teacher.getTeacherNo());
            row.setPickedCount(picked);
            row.setAllocatedCount(allocated);
            row.setCapacity(campaign.getSupervisorCapacity());
            row.setFreeCount(Math.max(0, campaign.getSupervisorCapacity() - mine.size()));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public List<Long> listUnassignedStudentIds(Long campaignId, String userType, Long userId, Long collegeId) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        // R-10.1：院系管理者强制限定本院系范围
        Long effectiveCollegeId = collegeId;
        if ("department".equals(userType)) {
            effectiveCollegeId = scopeResolver.deptCollegeId(userId);
        }
        List<DashboardRow> rows = dashboardQuery.queryRows(
                campaign.getId(), gradeIdsOf(campaign), effectiveCollegeId, null, null);
        return rows.stream()
                .filter(r -> r.getAssignmentId() == null)
                .map(DashboardRow::getStudentId)
                .toList();
    }

    // ---- helpers ----

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private void ensureStudentUnassigned(Long campaignId, Long studentId) {
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getStudentId, studentId));
        if (dup > 0) {
            throw new BusinessException(409, "该学生已被选择，先占先得");
        }
    }

    private long countTeacherAssignments(Long campaignId, Long teacherId, AssignmentSourceEnum source) {
        return baseMapper.selectCount(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getTeacherId, teacherId)
                .eq(source != null, GraduationAssignment::getSource, source));
    }

    private List<Long> gradeIdsOf(GraduationCampaign campaign) {
        Set<Long> ids = new HashSet<>();
        if (campaign.getAllowedGradeIds() != null) {
            for (String part : campaign.getAllowedGradeIds().split(",")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略非法段
                }
            }
        }
        return List.copyOf(ids);
    }

    private List<AssignmentResponse> toResponses(List<GraduationAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> personIds = new ArrayList<>();
        assignments.forEach(a -> {
            personIds.add(a.getStudentId());
            personIds.add(a.getTeacherId());
            if (a.getPrevTeacherId() != null) {
                personIds.add(a.getPrevTeacherId());
            }
        });
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        Map<Long, String> noMap = studentMapperNoMap(assignments);
        return assignments.stream().map(a -> {
            AssignmentResponse resp = new AssignmentResponse();
            resp.setId(a.getId());
            resp.setCampaignId(a.getCampaignId());
            resp.setStudentId(a.getStudentId());
            resp.setStudentName(nameMap.get(a.getStudentId()));
            resp.setStudentNo(noMap.get(a.getStudentId()));
            resp.setTeacherId(a.getTeacherId());
            resp.setTeacherName(nameMap.get(a.getTeacherId()));
            resp.setSource(a.getSource());
            resp.setAssignTime(a.getAssignTime());
            resp.setPrevTeacherId(a.getPrevTeacherId());
            resp.setPrevTeacherName(a.getPrevTeacherId() != null ? nameMap.get(a.getPrevTeacherId()) : null);
            resp.setReassignReason(a.getReassignReason());
            resp.setReassignTime(a.getReassignTime());
            return resp;
        }).toList();
    }

    private Map<Long, String> studentMapperNoMap(List<GraduationAssignment> assignments) {
        List<Long> studentIds = assignments.stream()
                .map(GraduationAssignment::getStudentId).distinct().toList();
        return studentIds.isEmpty() ? Map.of() : studentMapper.toStudentNoMap(studentIds);
    }

    private AssignmentResponse toResponse(GraduationAssignment assignment) {
        return toResponses(List.of(assignment)).get(0);
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
