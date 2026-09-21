package com.xrq.xxq.module.coursework.assignment.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.assignment.dto.GradeRequest;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionRowView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmitRequest;
import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;
import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentSubmissionMapper;
import com.xrq.xxq.module.coursework.assignment.service.SubmissionService;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.CourseworkFileSupport;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.notification.notice.CourseworkNoticeScenes;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl
        extends ServiceImpl<CourseAssignmentSubmissionMapper, CourseAssignmentSubmission>
        implements SubmissionService {

    private final CourseAssignmentMapper assignmentMapper;
    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;
    private final CourseworkNoticeScenes courseworkNoticeScenes;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;

    /** 迟交判定：提交时间晚于截止时间为迟交（恰好相等不算）。包级可见供单测。 */
    static int lateOf(LocalDateTime submitTime, LocalDateTime deadline) {
        return submitTime.isAfter(deadline) ? 1 : 0;
    }

    @Override
    @Transactional
    public SubmissionView submit(Long studentUserId, Long assignmentId, SubmitRequest req, MultipartFile file) {
        CourseAssignment a = assignmentMapper.selectById(assignmentId);
        // 草稿对学生按不存在处理
        if (a == null || a.getStatus() == AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(404, "作业不存在");
        }
        if (a.getStatus() == AssignmentStatusEnum.CLOSED) {
            throw new BusinessException(400, "作业已关闭，停止提交");
        }
        groupResolver.assertVisibleToStudent(a.getTeachInfoId(), studentUserId);

        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file,
                FileBizEnum.COURSE_ASSIGNMENT_SUBMISSION, false);
        boolean hasContent = req.getContent() != null && !req.getContent().isBlank();
        if (!hasContent && ref == null) {
            throw new BusinessException(400, "提交内容不能为空（文本或附件至少一项）");
        }

        LocalDateTime now = LocalDateTime.now();
        // 应用层 upsert（全库约定：无 DB 唯一约束）。并发双击可能产生重复行，
        // 读取侧按 id 倒序取首条兜底（见 mySubmission/roster）。
        CourseAssignmentSubmission s = baseMapper.selectOne(
                new LambdaQueryWrapper<CourseAssignmentSubmission>()
                        .eq(CourseAssignmentSubmission::getAssignmentId, assignmentId)
                        .eq(CourseAssignmentSubmission::getStudentId, studentUserId)
                        .orderByDesc(CourseAssignmentSubmission::getId)
                        .last("LIMIT 1"));
        if (s == null) {
            s = new CourseAssignmentSubmission();
            s.setAssignmentId(assignmentId);
            s.setStudentId(studentUserId);
            s.setVersion(1);
            s.setCreateTime(now);
        } else {
            s.setVersion(s.getVersion() + 1);
        }
        s.setContent(req.getContent());
        if (ref != null) {
            // 旧附件不删：内容寻址产物可能被共享，由 FileMaintenanceTask 回收
            s.setFileName(ref.storedPath());
            s.setFileOriginal((req.getFileOriginal() == null || req.getFileOriginal().isBlank())
                    ? ref.originalName() : req.getFileOriginal().trim());
        } else if (s.getId() == null) {
            s.setFileName(null);
            s.setFileOriginal(null);
        }
        // 重交不带新附件时保留旧附件（上面 else 分支仅处理新建）
        s.setSubmitTime(now);
        s.setLate(lateOf(now, a.getDeadline()));
        // 重交重置批改痕迹，教师需重新批改
        s.setStatus(SubmissionStatusEnum.SUBMITTED);
        s.setScore(null);
        s.setComment(null);
        s.setGradeTime(null);
        s.setUpdateTime(now);
        if (s.getId() == null) {
            baseMapper.insert(s);
        } else {
            baseMapper.updateById(s);
        }
        return toView(s);
    }

    @Override
    public SubmissionView mySubmission(Long studentUserId, Long assignmentId) {
        CourseAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || a.getStatus() == AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.assertVisibleToStudent(a.getTeachInfoId(), studentUserId);
        CourseAssignmentSubmission s = latestOf(assignmentId, studentUserId);
        return s == null ? null : toView(s);
    }

    @Override
    public List<SubmissionRowView> roster(Long teacherUserId, Long assignmentId) {
        CourseAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);

        List<Long> rosterIds = groupResolver.rosterUserIds(a.getTeachInfoId());
        if (rosterIds.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseAssignmentSubmission> byStudent = baseMapper.selectList(
                        new LambdaQueryWrapper<CourseAssignmentSubmission>()
                                .eq(CourseAssignmentSubmission::getAssignmentId, assignmentId)
                                .in(CourseAssignmentSubmission::getStudentId, rosterIds)
                                .orderByDesc(CourseAssignmentSubmission::getId)).stream()
                // 并发重复行兜底：id 大者（最新）生效
                .collect(Collectors.toMap(CourseAssignmentSubmission::getStudentId, Function.identity(),
                        (x, y) -> x.getId() >= y.getId() ? x : y));
        Map<Long, String> nameMap = userMapper.selectByIds(rosterIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (x, y) -> x));
        Map<Long, String> studentNoMap = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getUserId, rosterIds)).stream()
                .collect(Collectors.toMap(Student::getUserId, Student::getStudentNo, (x, y) -> x));

        return rosterIds.stream().map(sid -> {
            SubmissionRowView row = new SubmissionRowView();
            row.setStudentUserId(sid);
            row.setStudentName(nameMap.get(sid));
            row.setStudentNo(studentNoMap.get(sid));
            CourseAssignmentSubmission s = byStudent.get(sid);
            row.setSubmitted(s != null);
            if (s != null) {
                row.setSubmissionId(s.getId());
                row.setLate(s.getLate());
                row.setStatus(s.getStatus());
                row.setScore(s.getScore());
                row.setComment(s.getComment());
                row.setVersion(s.getVersion());
                row.setSubmitTime(s.getSubmitTime());
            }
            return row;
        }).sorted(Comparator.comparing(SubmissionRowView::getStudentNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    @Transactional
    public SubmissionView grade(Long teacherUserId, Long submissionId, GradeRequest req) {
        CourseAssignmentSubmission s = baseMapper.selectById(submissionId);
        if (s == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        CourseAssignment a = assignmentMapper.selectById(s.getAssignmentId());
        if (a == null) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);
        if (req.getScore() == null) {
            throw new BusinessException(400, "分数不能为空");
        }
        if (req.getScore().signum() < 0 || req.getScore().compareTo(a.getTotalScore()) > 0) {
            throw new BusinessException(400, "分数须在 0 与满分（" + a.getTotalScore() + "）之间");
        }
        s.setScore(req.getScore());
        s.setComment(req.getComment());
        s.setStatus(SubmissionStatusEnum.GRADED);
        s.setGradeTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(s);

        courseworkNoticeScenes.assignmentGraded(s.getStudentId(), a.getTitle(),
                req.getScore().stripTrailingZeros().toPlainString());
        return toView(s);
    }

    private CourseAssignmentSubmission latestOf(Long assignmentId, Long studentUserId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<CourseAssignmentSubmission>()
                .eq(CourseAssignmentSubmission::getAssignmentId, assignmentId)
                .eq(CourseAssignmentSubmission::getStudentId, studentUserId)
                .orderByDesc(CourseAssignmentSubmission::getId)
                .last("LIMIT 1"));
    }

    private SubmissionView toView(CourseAssignmentSubmission s) {
        SubmissionView v = new SubmissionView();
        v.setId(s.getId());
        v.setAssignmentId(s.getAssignmentId());
        v.setContent(s.getContent());
        v.setFileName(s.getFileName());
        v.setFileOriginal(s.getFileOriginal());
        v.setSubmitTime(s.getSubmitTime());
        v.setLate(s.getLate());
        v.setStatus(s.getStatus());
        v.setScore(s.getScore());
        v.setComment(s.getComment());
        v.setVersion(s.getVersion());
        return v;
    }
}
