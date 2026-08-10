package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.practice.graduation.dto.AllocationRequest;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentResponse;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignResponse;
import com.xrq.xxq.module.practice.graduation.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.practice.graduation.dto.GraduationExportRow;
import com.xrq.xxq.module.practice.graduation.dto.PickRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalDeclareRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalResponse;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewRequest;
import com.xrq.xxq.module.practice.graduation.entity.AssignmentSourceEnum;
import com.xrq.xxq.module.practice.graduation.entity.AssignmentStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.GraduationAssignment;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.entity.GraduationProposal;
import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.Thesis;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationAssignmentMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationProposalMapper;
import com.xrq.xxq.module.practice.graduation.mapper.ThesisMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationServiceImpl
        extends ServiceImpl<GraduationCampaignMapper, GraduationCampaign>
        implements GraduationService {

    private final GraduationProposalMapper proposalMapper;
    private final GraduationAssignmentMapper assignmentMapper;
    private final ThesisMapper thesisMapper;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassNameMapper classNameMapper;
    private final CollegeMapper collegeMapper;
    private final SemesterService semesterService;
    private final StudentScopeResolver scopeResolver;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 教务 ====================

    @Override
    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest request) {
        ParamValidator.requireNonBlank(request.getTitle(), "活动标题");
        ParamValidator.requireNonNull(request.getSupervisorCapacity(), "每教师最大指导数");
        ParamValidator.requireNonNull(request.getFreeSelectCapacity(), "每教师自选数");
        validateCapacity(request.getSupervisorCapacity(), request.getFreeSelectCapacity());
        GraduationCampaign campaign = new GraduationCampaign();
        campaign.setSemesterId(semesterService.resolveOrDefault(request.getSemesterId()));
        campaign.setTitle(request.getTitle());
        campaign.setDeadline(request.getDeadline());
        campaign.setSupervisorCapacity(request.getSupervisorCapacity());
        campaign.setFreeSelectCapacity(request.getFreeSelectCapacity());
        campaign.setAllowedGradeIds(joinGradeIds(request.getAllowedGradeIds()));
        campaign.setStatus(CampaignStatusEnum.DRAFT);
        save(campaign);
        return toCampaignResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse updateCampaign(Long id, CampaignUpdateRequest request) {
        GraduationCampaign campaign = requireCampaign(id);
        if (request.getTitle() != null) {
            ParamValidator.requireNonBlank(request.getTitle(), "活动标题");
            campaign.setTitle(request.getTitle());
        }
        if (request.getDeadline() != null) {
            campaign.setDeadline(request.getDeadline());
        }
        Integer supervisor = request.getSupervisorCapacity() != null ? request.getSupervisorCapacity() : campaign.getSupervisorCapacity();
        Integer free = request.getFreeSelectCapacity() != null ? request.getFreeSelectCapacity() : campaign.getFreeSelectCapacity();
        validateCapacity(supervisor, free);
        if (request.getSupervisorCapacity() != null) {
            campaign.setSupervisorCapacity(request.getSupervisorCapacity());
        }
        if (request.getFreeSelectCapacity() != null) {
            campaign.setFreeSelectCapacity(request.getFreeSelectCapacity());
        }
        if (request.getAllowedGradeIds() != null) {
            campaign.setAllowedGradeIds(joinGradeIds(request.getAllowedGradeIds()));
        }
        if (request.getStatus() != null) {
            campaign.setStatus(request.getStatus());
        }
        updateById(campaign);
        return toCampaignResponse(campaign);
    }

    @Override
    @Transactional
    public void changeCampaignStatus(Long id, CampaignStatusEnum status) {
        ParamValidator.requireNonNull(status, "状态");
        GraduationCampaign campaign = requireCampaign(id);
        campaign.setStatus(status);
        updateById(campaign);
    }

    @Override
    public PageResult<CampaignResponse> listCampaigns(PageQuery pageQuery) {
        Page<GraduationCampaign> page = baseMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<GraduationCampaign>().orderByDesc(GraduationCampaign::getId));
        return PageResult.of(page, page.getRecords().stream().map(this::toCampaignResponse).toList());
    }

    @Override
    public CampaignResponse getCampaign(Long id) {
        return toCampaignResponse(requireCampaign(id));
    }

    @Override
    @Transactional
    public AssignmentResponse reviewAssignment(Long assignmentId, AssignmentReviewRequest request) {
        ParamValidator.requireNonNull(request.getApproved(), "审查结果");
        GraduationAssignment assignment = requireAssignment(assignmentId);
        if (assignment.getStatus() != AssignmentStatusEnum.MATCHED) {
            throw new BusinessException(409, "该匹配记录已审查");
        }
        assignment.setReviewTime(LocalDateTime.now());
        assignment.setReviewComment(request.getComment());
        if (request.getApproved()) {
            assignment.setStatus(AssignmentStatusEnum.APPROVED);
            assignmentMapper.updateById(assignment);
        } else {
            // 驳回：匹配记录置 REJECTED（保留审计），申报回匹配池
            assignment.setStatus(AssignmentStatusEnum.REJECTED);
            assignmentMapper.updateById(assignment);
            GraduationProposal proposal = proposalMapper.selectById(assignment.getProposalId());
            if (proposal != null && proposal.getStatus() == ProposalStatusEnum.ASSIGNED) {
                proposal.setStatus(ProposalStatusEnum.DEPT_APPROVED);
                proposalMapper.updateById(proposal);
            }
        }
        notify(assignment.getStudentId(), "毕业选题匹配审查结果",
                request.getApproved() ? "您的选题匹配已通过教务审查。" : "您的选题匹配被教务驳回，已退回匹配池。");
        return toAssignmentResponse(assignment);
    }

    @Override
    public List<GraduationExportRow> exportAssignments(Long campaignId) {
        requireCampaign(campaignId);
        List<GraduationProposal> proposals = proposalMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, campaignId)
                .orderByAsc(GraduationProposal::getId));
        List<GraduationAssignment> assignments = assignmentMapper.selectList(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED));
        Map<Long, GraduationAssignment> assignmentByProposal = assignments.stream()
                .collect(Collectors.toMap(GraduationAssignment::getProposalId, a -> a, (a, b) -> a));

        List<Long> studentIds = proposals.stream().map(GraduationProposal::getStudentId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = assignments.stream().map(GraduationAssignment::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> studentNameMap = userMapper.toNameMap(studentIds);
        Map<Long, String> studentNoMap = studentMapper.toStudentNoMap(studentIds);
        Map<Long, String> teacherNameMap = userMapper.toNameMap(teacherIds);
        Map<Long, String> teacherNoMap = teacherNoMap(teacherIds);
        Map<Long, Long> studentCollege = resolveStudentColleges(studentIds);
        Set<Long> collegeIds = new HashSet<>();
        studentCollege.values().stream().filter(Objects::nonNull).forEach(collegeIds::add);
        collegeIds.addAll(assignments.stream().map(GraduationAssignment::getCollegeId).filter(Objects::nonNull).toList());
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(collegeIds);

        List<GraduationExportRow> rows = new ArrayList<>();
        for (GraduationProposal p : proposals) {
            GraduationExportRow row = new GraduationExportRow();
            row.setStudentNo(studentNoMap.get(p.getStudentId()));
            row.setStudentName(studentNameMap.get(p.getStudentId()));
            Long colId = studentCollege.get(p.getStudentId());
            row.setCollegeName(colId != null ? collegeNameMap.get(colId) : null);
            row.setProposalTitle(p.getTitle());
            GraduationAssignment a = assignmentByProposal.get(p.getId());
            if (a != null) {
                row.setTeacherNo(teacherNoMap.get(a.getTeacherId()));
                row.setTeacherName(teacherNameMap.get(a.getTeacherId()));
                row.setSource(a.getSource().getDescription());
                row.setStatus(a.getStatus().getDescription());
            } else {
                row.setSource("未匹配");
                row.setStatus("未匹配");
            }
            rows.add(row);
        }
        return rows;
    }

    // ==================== 学生 ====================

    @Override
    @Transactional
    public ProposalResponse declareProposal(Long studentUserId, ProposalDeclareRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "选题活动");
        ParamValidator.requireNonBlank(request.getTitle(), "选题标题");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "选题活动未开放");
        }
        if (campaign.getDeadline() != null && LocalDateTime.now().isAfter(campaign.getDeadline())) {
            throw new BusinessException(409, "选题申报已截止");
        }
        // 年级限制
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (student == null) {
            throw new BusinessException(403, "权限不足");
        }
        List<Long> allowed = parseGradeIds(campaign.getAllowedGradeIds());
        if (!allowed.isEmpty() && (student.getGradeId() == null || !allowed.contains(student.getGradeId()))) {
            throw new BusinessException(403, "权限不足");
        }
        // 应用层查重：每活动每学生仅一条申报
        Long dup = proposalMapper.selectCount(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, request.getCampaignId())
                .eq(GraduationProposal::getStudentId, studentUserId));
        if (dup != null && dup > 0) {
            throw new BusinessException(409, "本活动已申报选题");
        }
        GraduationProposal proposal = new GraduationProposal();
        proposal.setCampaignId(request.getCampaignId());
        proposal.setStudentId(studentUserId);
        proposal.setTitle(request.getTitle());
        proposal.setDescription(request.getDescription());
        proposal.setRequirements(request.getRequirements());
        proposal.setStatus(ProposalStatusEnum.PENDING_DEPT);
        proposal.setCreateTime(LocalDateTime.now());
        proposalMapper.insert(proposal);
        return toProposalResponse(proposal);
    }

    @Override
    @Transactional
    public void cancelProposal(Long studentUserId, Long proposalId) {
        GraduationProposal proposal = proposalMapper.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(404, "申报不存在");
        }
        if (!proposal.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (proposal.getStatus() != ProposalStatusEnum.PENDING_DEPT) {
            throw new BusinessException(409, "已进入审核流程，不可撤销");
        }
        proposalMapper.deleteById(proposalId);
    }

    @Override
    public List<ProposalResponse> listMyProposals(Long studentUserId) {
        List<GraduationProposal> proposals = proposalMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getStudentId, studentUserId)
                .orderByDesc(GraduationProposal::getId));
        return toProposalResponses(proposals);
    }

    @Override
    public AssignmentResponse getMyAssignment(Long studentUserId, Long campaignId) {
        GraduationAssignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getStudentId, studentUserId)
                .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED)
                .last("LIMIT 1"));
        return assignment == null ? null : toAssignmentResponse(assignment);
    }

    // ==================== 院系管理者 ====================

    @Override
    @Transactional
    public ProposalResponse reviewProposal(Long deptUserId, Long proposalId, ProposalReviewRequest request) {
        ParamValidator.requireNonNull(request.getApproved(), "审核结果");
        GraduationProposal proposal = proposalMapper.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(404, "申报不存在");
        }
        if (proposal.getStatus() != ProposalStatusEnum.PENDING_DEPT) {
            throw new BusinessException(409, "该申报已审核");
        }
        ensureDeptOwnsStudent(deptUserId, proposal.getStudentId());
        proposal.setStatus(request.getApproved() ? ProposalStatusEnum.DEPT_APPROVED : ProposalStatusEnum.DEPT_REJECTED);
        proposal.setDeptReviewerId(deptUserId);
        proposal.setDeptReviewTime(LocalDateTime.now());
        proposal.setDeptReviewComment(request.getComment());
        proposalMapper.updateById(proposal);
        notify(proposal.getStudentId(), "毕业选题院系初审结果",
                request.getApproved() ? "您的选题《" + proposal.getTitle() + "》已通过院系初审，进入匹配池。"
                        : "您的选题《" + proposal.getTitle() + "》被院系初审驳回。");
        return toProposalResponse(proposal);
    }

    @Override
    @Transactional
    public AssignmentResponse allocateStudent(Long deptUserId, AllocationRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "选题活动");
        ParamValidator.requireNonNull(request.getProposalId(), "选题申报");
        ParamValidator.requireNonNull(request.getTeacherId(), "指导教师");
        GraduationCampaign campaign = requireOpenCampaign(request.getCampaignId());
        GraduationProposal proposal = requirePoolProposal(request.getProposalId(), campaign.getId());
        ensureDeptOwnsStudent(deptUserId, proposal.getStudentId());
        // 教师：本院系且未满
        Teacher teacher = teacherMapper.findByUserId(request.getTeacherId());
        if (teacher == null) {
            throw new BusinessException(404, "教师不存在");
        }
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        if (deptCollegeId == null || !Objects.equals(deptCollegeId, teacher.getCollegeId())) {
            throw new BusinessException(403, "权限不足");
        }
        ensureStudentNotAssigned(campaign.getId(), proposal.getStudentId());
        Long total = countTeacherAssigned(campaign.getId(), request.getTeacherId());
        if (total >= campaign.getSupervisorCapacity()) {
            throw new BusinessException(409, "该教师指导名额已满");
        }
        GraduationAssignment assignment = buildAssignment(campaign, proposal, request.getTeacherId(),
                teacher.getCollegeId(), AssignmentSourceEnum.DEPT_ALLOCATE);
        assignmentMapper.insert(assignment);
        markProposalAssigned(proposal);
        notify(proposal.getStudentId(), "毕业选题匹配结果",
                "院系已为您分配指导教师，请查看匹配详情。");
        return toAssignmentResponse(assignment);
    }

    @Override
    public List<ProposalResponse> listDeptPool(Long deptUserId, Long campaignId) {
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        if (deptCollegeId == null) {
            return List.of();
        }
        List<GraduationProposal> proposals = proposalMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, campaignId)
                .eq(GraduationProposal::getStatus, ProposalStatusEnum.DEPT_APPROVED)
                .orderByDesc(GraduationProposal::getId));
        if (proposals.isEmpty()) {
            return List.of();
        }
        // 仅本学院且未匹配
        List<Long> studentIds = proposals.stream().map(GraduationProposal::getStudentId).distinct().toList();
        Map<Long, Long> studentCollege = resolveStudentColleges(studentIds);
        Set<Long> assignedStudentIds = activeAssignedStudentIds(campaignId);
        List<GraduationProposal> filtered = proposals.stream()
                .filter(p -> Objects.equals(studentCollege.get(p.getStudentId()), deptCollegeId))
                .filter(p -> !assignedStudentIds.contains(p.getStudentId()))
                .toList();
        return toProposalResponses(filtered);
    }

    @Override
    public List<AssignmentResponse> listDeptAssignments(Long deptUserId, Long campaignId) {
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        if (deptCollegeId == null) {
            return List.of();
        }
        List<GraduationAssignment> assignments = assignmentMapper.selectList(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getCollegeId, deptCollegeId)
                .orderByDesc(GraduationAssignment::getId));
        return toAssignmentResponses(assignments);
    }

    // ==================== 教师 ====================

    @Override
    @Transactional
    public AssignmentResponse pickStudent(Long teacherUserId, PickRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "选题活动");
        ParamValidator.requireNonNull(request.getProposalId(), "选题申报");
        GraduationCampaign campaign = requireOpenCampaign(request.getCampaignId());
        GraduationProposal proposal = requirePoolProposal(request.getProposalId(), campaign.getId());
        // 自选仅限本学院
        Long teacherCollegeId = scopeResolver.teacherCollegeId(teacherUserId);
        Long studentCollegeId = scopeResolver.studentCollegeId(proposal.getStudentId());
        if (teacherCollegeId == null || !Objects.equals(teacherCollegeId, studentCollegeId)) {
            throw new BusinessException(403, "权限不足");
        }
        ensureStudentNotAssigned(campaign.getId(), proposal.getStudentId());
        Long picked = assignmentMapper.selectCount(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaign.getId())
                .eq(GraduationAssignment::getTeacherId, teacherUserId)
                .eq(GraduationAssignment::getSource, AssignmentSourceEnum.TEACHER_PICK)
                .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED));
        if (picked != null && picked >= campaign.getFreeSelectCapacity()) {
            throw new BusinessException(409, "自选名额已满");
        }
        GraduationAssignment assignment = buildAssignment(campaign, proposal, teacherUserId,
                teacherCollegeId, AssignmentSourceEnum.TEACHER_PICK);
        assignmentMapper.insert(assignment);
        markProposalAssigned(proposal);
        notify(proposal.getStudentId(), "毕业选题匹配结果",
                "教师已选择您的选题，请查看匹配详情。");
        return toAssignmentResponse(assignment);
    }

    @Override
    @Transactional
    public void cancelPick(Long teacherUserId, Long assignmentId) {
        GraduationAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(404, "匹配记录不存在");
        }
        if (!assignment.getTeacherId().equals(teacherUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (assignment.getSource() != AssignmentSourceEnum.TEACHER_PICK) {
            throw new BusinessException(409, "仅可撤销教师自选记录");
        }
        if (assignment.getStatus() == AssignmentStatusEnum.APPROVED) {
            throw new BusinessException(409, "已通过教务审查，不可撤销");
        }
        Long thesisCount = thesisMapper.selectCount(new LambdaQueryWrapper<Thesis>()
                .eq(Thesis::getAssignmentId, assignmentId));
        if (thesisCount != null && thesisCount > 0) {
            throw new BusinessException(409, "学生已提交论文，不可撤销自选");
        }
        assignmentMapper.deleteById(assignmentId);
        GraduationProposal proposal = proposalMapper.selectById(assignment.getProposalId());
        if (proposal != null && proposal.getStatus() == ProposalStatusEnum.ASSIGNED) {
            proposal.setStatus(ProposalStatusEnum.DEPT_APPROVED);
            proposalMapper.updateById(proposal);
        }
    }

    @Override
    public List<AssignmentResponse> listTeacherAssignments(Long teacherUserId, Long campaignId) {
        LambdaQueryWrapper<GraduationAssignment> w = new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getTeacherId, teacherUserId)
                .orderByDesc(GraduationAssignment::getId);
        if (campaignId != null) {
            w.eq(GraduationAssignment::getCampaignId, campaignId);
        }
        return toAssignmentResponses(assignmentMapper.selectList(w));
    }

    @Override
    public List<ProposalResponse> listPickableProposals(Long teacherUserId, Long campaignId) {
        Long teacherCollegeId = scopeResolver.teacherCollegeId(teacherUserId);
        if (teacherCollegeId == null) {
            return List.of();
        }
        List<GraduationProposal> proposals = proposalMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, campaignId)
                .eq(GraduationProposal::getStatus, ProposalStatusEnum.DEPT_APPROVED)
                .orderByDesc(GraduationProposal::getId));
        if (proposals.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = proposals.stream().map(GraduationProposal::getStudentId).distinct().toList();
        Map<Long, Long> studentCollege = resolveStudentColleges(studentIds);
        Set<Long> assignedStudentIds = activeAssignedStudentIds(campaignId);
        List<GraduationProposal> filtered = proposals.stream()
                .filter(p -> Objects.equals(studentCollege.get(p.getStudentId()), teacherCollegeId))
                .filter(p -> !assignedStudentIds.contains(p.getStudentId()))
                .toList();
        return toProposalResponses(filtered);
    }

    // ==================== helpers ====================

    private void validateCapacity(Integer supervisor, Integer free) {
        if (supervisor == null || supervisor <= 0) {
            throw new BusinessException(400, "每教师最大指导数必须大于0");
        }
        if (free == null || free <= 0) {
            throw new BusinessException(400, "每教师自选数必须大于0");
        }
        if (free > supervisor) {
            throw new BusinessException(400, "每教师自选数不能大于最大指导数");
        }
    }

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = baseMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选题活动不存在");
        }
        return campaign;
    }

    private GraduationCampaign requireOpenCampaign(Long id) {
        GraduationCampaign campaign = requireCampaign(id);
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "选题活动未开放");
        }
        return campaign;
    }

    private GraduationAssignment requireAssignment(Long id) {
        GraduationAssignment assignment = assignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException(404, "匹配记录不存在");
        }
        return assignment;
    }

    private GraduationProposal requirePoolProposal(Long proposalId, Long campaignId) {
        GraduationProposal proposal = proposalMapper.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(404, "选题申报不存在");
        }
        if (!proposal.getCampaignId().equals(campaignId)) {
            throw new BusinessException(409, "申报不属于该活动");
        }
        if (proposal.getStatus() != ProposalStatusEnum.DEPT_APPROVED) {
            throw new BusinessException(409, "该申报不在匹配池中");
        }
        return proposal;
    }

    private void ensureDeptOwnsStudent(Long deptUserId, Long studentUserId) {
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        Long studentCollegeId = scopeResolver.studentCollegeId(studentUserId);
        if (deptCollegeId == null || !Objects.equals(deptCollegeId, studentCollegeId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    private void ensureStudentNotAssigned(Long campaignId, Long studentId) {
        Long exist = assignmentMapper.selectCount(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getStudentId, studentId)
                .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED));
        if (exist != null && exist > 0) {
            throw new BusinessException(409, "该学生已匹配");
        }
    }

    private long countTeacherAssigned(Long campaignId, Long teacherUserId) {
        Long count = assignmentMapper.selectCount(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getTeacherId, teacherUserId)
                .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED));
        return count == null ? 0 : count;
    }

    private Set<Long> activeAssignedStudentIds(Long campaignId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<GraduationAssignment>()
                        .eq(GraduationAssignment::getCampaignId, campaignId)
                        .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED))
                .stream().map(GraduationAssignment::getStudentId).collect(Collectors.toSet());
    }

    private GraduationAssignment buildAssignment(GraduationCampaign campaign, GraduationProposal proposal,
                                                 Long teacherUserId, Long collegeId, AssignmentSourceEnum source) {
        GraduationAssignment assignment = new GraduationAssignment();
        assignment.setCampaignId(campaign.getId());
        assignment.setProposalId(proposal.getId());
        assignment.setStudentId(proposal.getStudentId());
        assignment.setTeacherId(teacherUserId);
        assignment.setCollegeId(collegeId);
        assignment.setSource(source);
        assignment.setStatus(AssignmentStatusEnum.MATCHED);
        assignment.setAssignTime(LocalDateTime.now());
        return assignment;
    }

    private void markProposalAssigned(GraduationProposal proposal) {
        proposal.setStatus(ProposalStatusEnum.ASSIGNED);
        proposalMapper.updateById(proposal);
    }

    private void notify(Long studentUserId, String title, String content) {
        if (studentUserId == null) {
            return;
        }
        eventPublisher.publishEvent(new PracticeNoticeEvent(studentUserId, title, content));
    }

    private String joinGradeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseGradeIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } })
                .filter(Objects::nonNull).toList();
    }

    /** 批量解析学生 user.id -> college.id（经 class_name.college_id）。 */
    private Map<Long, Long> resolveStudentColleges(List<Long> studentUserIds) {
        Map<Long, Long> result = new HashMap<>();
        if (studentUserIds == null || studentUserIds.isEmpty()) {
            return result;
        }
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getUserId, studentUserIds));
        Set<Long> classIds = students.stream().map(Student::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Long> classCollege = classIds.isEmpty() ? Map.of()
                : classNameMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(ClassName::getId, ClassName::getCollegeId, (a, b) -> a));
        for (Student s : students) {
            result.put(s.getUserId(), s.getClassId() != null ? classCollege.get(s.getClassId()) : null);
        }
        return result;
    }

    private Map<Long, String> teacherNoMap(List<Long> teacherUserIds) {
        if (teacherUserIds == null || teacherUserIds.isEmpty()) {
            return Map.of();
        }
        return teacherMapper.selectList(new LambdaQueryWrapper<Teacher>().in(Teacher::getUserId, teacherUserIds)).stream()
                .filter(t -> t.getUserId() != null && t.getTeacherNo() != null)
                .collect(Collectors.toMap(Teacher::getUserId, Teacher::getTeacherNo, (a, b) -> a));
    }

    private CampaignResponse toCampaignResponse(GraduationCampaign campaign) {
        CampaignResponse resp = new CampaignResponse();
        resp.setId(campaign.getId());
        resp.setSemesterId(campaign.getSemesterId());
        resp.setTitle(campaign.getTitle());
        resp.setDeadline(campaign.getDeadline());
        resp.setSupervisorCapacity(campaign.getSupervisorCapacity());
        resp.setFreeSelectCapacity(campaign.getFreeSelectCapacity());
        resp.setAllowedGradeIds(parseGradeIds(campaign.getAllowedGradeIds()));
        resp.setStatus(campaign.getStatus());
        resp.setCreateTime(campaign.getCreateTime());
        return resp;
    }

    private ProposalResponse toProposalResponse(GraduationProposal proposal) {
        List<ProposalResponse> list = toProposalResponses(List.of(proposal));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<ProposalResponse> toProposalResponses(List<GraduationProposal> proposals) {
        if (proposals.isEmpty()) {
            return List.of();
        }
        List<Long> campaignIds = proposals.stream().map(GraduationProposal::getCampaignId).distinct().toList();
        Map<Long, String> campaignTitle = baseMapper.selectByIds(campaignIds).stream()
                .collect(Collectors.toMap(GraduationCampaign::getId, GraduationCampaign::getTitle, (a, b) -> a));
        List<Long> proposalIds = proposals.stream().map(GraduationProposal::getId).toList();
        List<Long> studentIds = proposals.stream().map(GraduationProposal::getStudentId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> studentNameMap = userMapper.toNameMap(studentIds);
        Map<Long, String> studentNoMap = studentMapper.toStudentNoMap(studentIds);
        Map<Long, Long> studentCollege = resolveStudentColleges(studentIds);
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(
                studentCollege.values().stream().filter(Objects::nonNull).distinct().toList());
        Map<Long, GraduationAssignment> assignmentByProposal = assignmentMapper.selectList(
                        new LambdaQueryWrapper<GraduationAssignment>().in(GraduationAssignment::getProposalId, proposalIds)
                                .ne(GraduationAssignment::getStatus, AssignmentStatusEnum.REJECTED))
                .stream().collect(Collectors.toMap(GraduationAssignment::getProposalId, a -> a, (a, b) -> a));
        List<Long> teacherIds = assignmentByProposal.values().stream().map(GraduationAssignment::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> teacherNameMap = userMapper.toNameMap(teacherIds);
        Map<Long, String> teacherNoMap = teacherNoMap(teacherIds);

        return proposals.stream().map(p -> {
            ProposalResponse resp = new ProposalResponse();
            resp.setId(p.getId());
            resp.setCampaignId(p.getCampaignId());
            resp.setCampaignTitle(campaignTitle.get(p.getCampaignId()));
            resp.setStudentId(p.getStudentId());
            resp.setStudentName(studentNameMap.get(p.getStudentId()));
            resp.setStudentNo(studentNoMap.get(p.getStudentId()));
            Long colId = studentCollege.get(p.getStudentId());
            resp.setCollegeId(colId);
            resp.setCollegeName(colId != null ? collegeNameMap.get(colId) : null);
            resp.setTitle(p.getTitle());
            resp.setDescription(p.getDescription());
            resp.setRequirements(p.getRequirements());
            resp.setStatus(p.getStatus() == null ? null : p.getStatus().getDescription());
            resp.setDeptReviewComment(p.getDeptReviewComment());
            resp.setDeptReviewTime(p.getDeptReviewTime());
            resp.setCreateTime(p.getCreateTime());
            GraduationAssignment a = assignmentByProposal.get(p.getId());
            if (a != null) {
                resp.setAssignmentId(a.getId());
                resp.setTeacherId(a.getTeacherId());
                resp.setTeacherName(teacherNameMap.get(a.getTeacherId()));
                resp.setTeacherNo(teacherNoMap.get(a.getTeacherId()));
                resp.setAssignmentSource(a.getSource() == null ? null : a.getSource().getDescription());
                resp.setAssignmentStatus(a.getStatus() == null ? null : a.getStatus().getDescription());
            }
            return resp;
        }).toList();
    }

    private AssignmentResponse toAssignmentResponse(GraduationAssignment assignment) {
        List<AssignmentResponse> list = toAssignmentResponses(List.of(assignment));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<AssignmentResponse> toAssignmentResponses(List<GraduationAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> campaignIds = assignments.stream().map(GraduationAssignment::getCampaignId).distinct().toList();
        Map<Long, String> campaignTitle = baseMapper.selectByIds(campaignIds).stream()
                .collect(Collectors.toMap(GraduationCampaign::getId, GraduationCampaign::getTitle, (a, b) -> a));
        List<Long> proposalIds = assignments.stream().map(GraduationAssignment::getProposalId).distinct().toList();
        Map<Long, String> proposalTitle = proposalMapper.selectByIds(proposalIds).stream()
                .collect(Collectors.toMap(GraduationProposal::getId, GraduationProposal::getTitle, (a, b) -> a));
        List<Long> studentIds = assignments.stream().map(GraduationAssignment::getStudentId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = assignments.stream().map(GraduationAssignment::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> studentNameMap = userMapper.toNameMap(studentIds);
        Map<Long, String> studentNoMap = studentMapper.toStudentNoMap(studentIds);
        Map<Long, String> teacherNameMap = userMapper.toNameMap(teacherIds);
        Map<Long, String> teacherNoMap = teacherNoMap(teacherIds);
        Set<Long> collegeIds = assignments.stream().map(GraduationAssignment::getCollegeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(collegeIds);

        return assignments.stream().map(a -> {
            AssignmentResponse resp = new AssignmentResponse();
            resp.setId(a.getId());
            resp.setCampaignId(a.getCampaignId());
            resp.setCampaignTitle(campaignTitle.get(a.getCampaignId()));
            resp.setProposalId(a.getProposalId());
            resp.setProposalTitle(proposalTitle.get(a.getProposalId()));
            resp.setStudentId(a.getStudentId());
            resp.setStudentName(studentNameMap.get(a.getStudentId()));
            resp.setStudentNo(studentNoMap.get(a.getStudentId()));
            resp.setTeacherId(a.getTeacherId());
            resp.setTeacherName(teacherNameMap.get(a.getTeacherId()));
            resp.setTeacherNo(teacherNoMap.get(a.getTeacherId()));
            resp.setCollegeId(a.getCollegeId());
            resp.setCollegeName(collegeNameMap.get(a.getCollegeId()));
            resp.setSource(a.getSource() == null ? null : a.getSource().getDescription());
            resp.setStatus(a.getStatus() == null ? null : a.getStatus().getDescription());
            resp.setAssignTime(a.getAssignTime());
            resp.setReviewTime(a.getReviewTime());
            resp.setReviewComment(a.getReviewComment());
            return resp;
        }).toList();
    }
}
