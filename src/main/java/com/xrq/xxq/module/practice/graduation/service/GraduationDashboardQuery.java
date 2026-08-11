package com.xrq.xxq.module.practice.graduation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.college.entity.College;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.practice.graduation.dto.DashboardRow;
import com.xrq.xxq.module.practice.graduation.entity.GraduationAssignment;
import com.xrq.xxq.module.practice.graduation.entity.GraduationMidterm;
import com.xrq.xxq.module.practice.graduation.entity.GraduationProposal;
import com.xrq.xxq.module.practice.graduation.entity.GraduationProposalReview;
import com.xrq.xxq.module.practice.graduation.entity.ProposalReviewStageEnum;
import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationAssignmentMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationMidtermMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationProposalMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationProposalReviewMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.GradeMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 看板行组装查询（R-5.8/R-6.14），供看板/导出与师生匹配共用。
 * <p>
 * 替代原 {@code GraduationDashboardMapper} 的 8 表 JOIN + 行级相关子查询：
 * 改为各维度单表批量查询后在应用层合并（学生/用户/班级/院系/年级/申请/审批流水/匹配/中期各一次 IN 批查），
 * 院系/关键字/状态筛选也在内存完成，避免大结果集多表连接拖慢数据库。
 */
@Component
@RequiredArgsConstructor
public class GraduationDashboardQuery {

    /** 状态筛选特殊值：未开始选题（无申请记录） */
    public static final String STATUS_NOT_SUBMITTED = "NOT_SUBMITTED";
    /** 状态筛选特殊值：初审中（待院系初审 + 待教务终审 两态之和） */
    public static final String STATUS_PENDING = "PENDING";
    /** 审批流水动作：通过 */
    private static final String ACTION_APPROVE = "APPROVE";

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final CollegeMapper collegeMapper;
    private final GradeMapper gradeMapper;
    private final GraduationProposalMapper proposalMapper;
    private final GraduationProposalReviewMapper reviewMapper;
    private final GraduationAssignmentMapper assignmentMapper;
    private final GraduationMidtermMapper midtermMapper;

    /**
     * 查询看板行（应用层合并）。
     *
     * @param campaignId 活动ID
     * @param gradeIds   活动参与年级ID集合（空集合直接返回空列表）
     * @param collegeId  院系过滤（null 不过滤）
     * @param keyword    学号/姓名模糊搜索（null/空白 不过滤）
     * @param status     状态筛选：{@link #STATUS_NOT_SUBMITTED} / {@link #STATUS_PENDING} /
     *                   {@link ProposalStatusEnum} code，null/空白 不过滤
     * @return 按学生 user.id 升序的看板行（与原 SQL 的 ORDER BY s.user_id 一致）
     */
    public List<DashboardRow> queryRows(Long campaignId, List<Long> gradeIds, Long collegeId,
                                        String keyword, String status) {
        if (gradeIds == null || gradeIds.isEmpty()) {
            return List.of();
        }
        // 底表：参与年级的学生，按 user.id 升序
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .in(Student::getGradeId, gradeIds)
                .orderByAsc(Student::getUserId));
        if (students.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = students.stream().map(Student::getUserId).toList();

        // 各维度单表批查（user 逻辑删除由 MyBatis Plus 自动过滤，等价原 INNER JOIN u.deleted = 0）
        Map<Long, User> userMap = userMapper.selectByIds(studentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, ClassName> classMap = toIdMap(classNameMapper,
                students.stream().map(Student::getClassId).filter(Objects::nonNull).distinct().toList(),
                ClassName::getId);
        Map<Long, College> collegeMap = toIdMap(collegeMapper,
                classMap.values().stream().map(ClassName::getCollegeId).filter(Objects::nonNull).distinct().toList(),
                College::getId);
        Map<Long, Grade> gradeMap = toIdMap(gradeMapper, gradeIds, Grade::getId);

        // 选题申请：活动 + 学生范围内批查（R-5.3 每人一条，脏数据时取 id 最新）
        Map<Long, GraduationProposal> proposalMap = proposalMapper.selectList(
                        new LambdaQueryWrapper<GraduationProposal>()
                                .eq(GraduationProposal::getCampaignId, campaignId)
                                .in(GraduationProposal::getStudentId, studentIds)
                                .orderByDesc(GraduationProposal::getId))
                .stream().collect(Collectors.toMap(GraduationProposal::getStudentId, p -> p, (a, b) -> a));
        // 审批完成时间 = 教务终审通过时间（流水内存取 MAX，替代原行级相关子查询）
        Map<Long, LocalDateTime> approvedTimeMap = maxAcademicApproveTime(
                proposalMap.values().stream().map(GraduationProposal::getId).toList());
        // 师生匹配 + 教师姓名
        Map<Long, GraduationAssignment> assignmentMap = assignmentMapper.selectList(
                        new LambdaQueryWrapper<GraduationAssignment>()
                                .eq(GraduationAssignment::getCampaignId, campaignId)
                                .in(GraduationAssignment::getStudentId, studentIds)
                                .orderByDesc(GraduationAssignment::getId))
                .stream().collect(Collectors.toMap(GraduationAssignment::getStudentId, a -> a, (a, b) -> a));
        Map<Long, String> teacherNameMap = userMapper.toNameMap(
                assignmentMap.values().stream().map(GraduationAssignment::getTeacherId)
                        .filter(Objects::nonNull).distinct().toList());
        // 中期检查
        Map<Long, GraduationMidterm> midtermMap = midtermMapper.selectList(
                        new LambdaQueryWrapper<GraduationMidterm>()
                                .eq(GraduationMidterm::getCampaignId, campaignId)
                                .in(GraduationMidterm::getStudentId, studentIds)
                                .orderByDesc(GraduationMidterm::getId))
                .stream().collect(Collectors.toMap(GraduationMidterm::getStudentId, m -> m, (a, b) -> a));

        List<DashboardRow> rows = new ArrayList<>(students.size());
        for (Student s : students) {
            User u = userMap.get(s.getUserId());
            if (u == null) {
                // 等价原 INNER JOIN user：用户缺失（含已逻辑删除）的学生不出行
                continue;
            }
            rows.add(assembleRow(s, u, classMap, collegeMap, gradeMap,
                    proposalMap, approvedTimeMap, assignmentMap, teacherNameMap, midtermMap));
        }
        return rows.stream().filter(row -> matches(row, collegeId, keyword, status)).toList();
    }

    // ---- 合并与筛选 ----

    private DashboardRow assembleRow(Student s, User u,
                                     Map<Long, ClassName> classMap, Map<Long, College> collegeMap,
                                     Map<Long, Grade> gradeMap, Map<Long, GraduationProposal> proposalMap,
                                     Map<Long, LocalDateTime> approvedTimeMap,
                                     Map<Long, GraduationAssignment> assignmentMap,
                                     Map<Long, String> teacherNameMap, Map<Long, GraduationMidterm> midtermMap) {
        DashboardRow row = new DashboardRow();
        row.setStudentId(s.getUserId());
        row.setStudentNo(s.getStudentNo());
        row.setStudentName(u.getName());
        ClassName cls = s.getClassId() == null ? null : classMap.get(s.getClassId());
        if (cls != null) {
            row.setClassName(cls.getClassName());
            row.setCollegeId(cls.getCollegeId());
            College college = cls.getCollegeId() == null ? null : collegeMap.get(cls.getCollegeId());
            if (college != null) {
                row.setCollegeName(college.getCollegeName());
            }
        }
        Grade grade = s.getGradeId() == null ? null : gradeMap.get(s.getGradeId());
        if (grade != null) {
            row.setGradeName(grade.getName());
        }
        GraduationProposal p = proposalMap.get(s.getUserId());
        if (p != null) {
            row.setProposalId(p.getId());
            row.setProposalTitle(p.getTitle());
            row.setProposalContent(p.getContent());
            row.setProposalStatus(p.getStatus());
            row.setProposalSubmitTime(p.getSubmitTime());
            row.setProposalApprovedTime(approvedTimeMap.get(p.getId()));
        }
        GraduationAssignment a = assignmentMap.get(s.getUserId());
        if (a != null) {
            row.setAssignmentId(a.getId());
            row.setTeacherId(a.getTeacherId());
            row.setTeacherName(a.getTeacherId() == null ? null : teacherNameMap.get(a.getTeacherId()));
            row.setAssignmentSource(a.getSource());
        }
        GraduationMidterm m = midtermMap.get(s.getUserId());
        if (m != null) {
            row.setMidtermId(m.getId());
            row.setMidtermConclusion(m.getConclusion());
        }
        return row;
    }

    /** 内存筛选：院系 / 学号或姓名关键字 / 选题状态（语义与原 SQL 一致）。 */
    private boolean matches(DashboardRow row, Long collegeId, String keyword, String status) {
        if (collegeId != null && !collegeId.equals(row.getCollegeId())) {
            return false;
        }
        if (keyword != null && !keyword.isBlank()
                && !contains(row.getStudentName(), keyword) && !contains(row.getStudentNo(), keyword)) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return true;
        }
        if (STATUS_NOT_SUBMITTED.equals(status)) {
            return row.getProposalId() == null;
        }
        if (STATUS_PENDING.equals(status)) {
            return row.getProposalStatus() == ProposalStatusEnum.PENDING_DEPT
                    || row.getProposalStatus() == ProposalStatusEnum.DEPT_APPROVED;
        }
        return row.getProposalStatus() != null && row.getProposalStatus().getCode().equals(status);
    }

    /** 每份申请的教务终审通过时间（取最近一次 APPROVE 流水）。 */
    private Map<Long, LocalDateTime> maxAcademicApproveTime(List<Long> proposalIds) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        if (proposalIds.isEmpty()) {
            return map;
        }
        reviewMapper.selectList(new LambdaQueryWrapper<GraduationProposalReview>()
                        .in(GraduationProposalReview::getProposalId, proposalIds)
                        .eq(GraduationProposalReview::getStage, ProposalReviewStageEnum.ACADEMIC)
                        .eq(GraduationProposalReview::getAction, ACTION_APPROVE))
                .stream()
                .filter(r -> r.getReviewTime() != null)
                .forEach(r -> map.merge(r.getProposalId(), r.getReviewTime(), (a, b) -> a.isAfter(b) ? a : b));
        return map;
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }

    /** 单表批查并转 id -> 实体 Map（HashMap，允许 null key 查询）。 */
    private <T> Map<Long, T> toIdMap(BaseMapper<T> mapper, Collection<Long> ids, Function<T, Long> idGetter) {
        Map<Long, T> map = new HashMap<>();
        if (ids != null && !ids.isEmpty()) {
            mapper.selectByIds(ids).forEach(t -> map.put(idGetter.apply(t), t));
        }
        return map;
    }
}
