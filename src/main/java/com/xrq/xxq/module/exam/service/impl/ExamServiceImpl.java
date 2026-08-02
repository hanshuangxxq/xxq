package com.xrq.xxq.module.exam.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.exam.dto.ClassCourseOptionDto;
import com.xrq.xxq.module.exam.dto.ExamCreateRequest;
import com.xrq.xxq.module.exam.dto.ExamView;
import com.xrq.xxq.module.exam.dto.MakeupCandidateDto;
import com.xrq.xxq.module.exam.dto.MakeupExamCreateRequest;
import com.xrq.xxq.module.exam.entity.Exam;
import com.xrq.xxq.module.exam.entity.ExamStatusEnum;
import com.xrq.xxq.module.exam.entity.ExamStudent;
import com.xrq.xxq.module.exam.entity.ExamTypeEnum;
import com.xrq.xxq.module.exam.mapper.ExamMapper;
import com.xrq.xxq.module.exam.mapper.ExamStudentMapper;
import com.xrq.xxq.module.exam.service.ExamService;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.local.entity.Local;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.teachinfo.service.TeachInfoService;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 考试服务实现：教务安排期末/期中考试；学生查询含公选课（复用 TeachInfoService.listMyTeachInfoIds）。
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl extends ServiceImpl<ExamMapper, Exam> implements ExamService {

    private final ExamStudentMapper examStudentMapper;
    private final CourseMapper courseMapper;
    private final LocalMapper localMapper;
    private final TeacherMapper teacherMapper;
    private final TeachInfoMapper teachInfoMapper;
    private final TeachInfoService teachInfoService;
    private final ScoreMapper scoreMapper;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final SemesterService semesterService;

    // ==================== 教务增删改 ====================

    @Override
    @Transactional
    public ExamView create(ExamCreateRequest request, Long userId) {
        if (request.getExamType() == ExamTypeEnum.MAKEUP || request.getExamType() == ExamTypeEnum.RETAKE) {
            throw new BusinessException(400, "补考/重修请使用补考创建接口");
        }
        validate(request);
        if (request.getTeachInfoId() == null) {
            throw new BusinessException(400, "期末/期中考试必须关联授课安排");
        }
        Exam e = new Exam();
        copy(request, e);
        e.setStatus(ExamStatusEnum.SCHEDULED);
        e.setCreateUserId(userId);
        baseMapper.insert(e);
        return toViews(List.of(e)).getFirst();
    }

    @Override
    @Transactional
    public ExamView update(Long id, ExamCreateRequest request, Long userId) {
        Exam exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(404, "考试不存在");
        }
        validate(request);
        copy(request, exist);
        if (request.getStatus() != null) {
            exist.setStatus(request.getStatus());
        }
        baseMapper.updateById(exist);
        return toViews(List.of(exist)).getFirst();
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        if (baseMapper.selectById(id) == null) {
            throw new BusinessException(404, "考试不存在");
        }
        examStudentMapper.delete(new LambdaQueryWrapper<ExamStudent>().eq(ExamStudent::getExamId, id));
        baseMapper.deleteById(id);
    }

    // ==================== 查询 ====================

    @Override
    public List<ExamView> list(Long semesterId, Long courseId, ExamTypeEnum examType) {
        LambdaQueryWrapper<Exam> w = new LambdaQueryWrapper<Exam>().orderByDesc(Exam::getExamDate);
        if (semesterId != null) {
            w.eq(Exam::getSemesterId, semesterId);
        }
        if (courseId != null) {
            w.eq(Exam::getCourseId, courseId);
        }
        if (examType != null) {
            w.eq(Exam::getExamType, examType);
        }
        return toViews(baseMapper.selectList(w));
    }

    @Override
    public List<ExamView> listForTeacher(Long userId) {
        Teacher t = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
        if (t == null) {
            return List.of();
        }
        List<Long> courseIds = teachInfoMapper.selectList(
                        new LambdaQueryWrapper<TeachInfo>().eq(TeachInfo::getTeacherId, t.getId()))
                .stream().map(TeachInfo::getCourseId).distinct().toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return toViews(baseMapper.selectList(new LambdaQueryWrapper<Exam>()
                .in(Exam::getCourseId, courseIds).orderByAsc(Exam::getExamDate)));
    }

    @Override
    public List<ExamView> listMyExams(Long studentUserId) {
        // 常规/期中：学生可见 teachInfo（含公选课班）下的考试，再按排考班级过滤（单班级）
        List<Long> teachInfoIds = teachInfoService.listMyTeachInfoIds(studentUserId);
        String studentClassName = resolveStudentClassName(studentUserId);
        List<Exam> regular = teachInfoIds.isEmpty() ? List.of()
                : baseMapper.selectList(new LambdaQueryWrapper<Exam>()
                        .in(Exam::getTeachInfoId, teachInfoIds)
                        .in(Exam::getExamType, ExamTypeEnum.FINAL, ExamTypeEnum.MIDTERM)
                        .orderByAsc(Exam::getExamDate)).stream()
                        .filter(e -> e.getClassName() == null || e.getClassName().equals(studentClassName))
                        .toList();
        // 补考/重修：考生名单命中
        List<Long> makeupExamIds = examStudentMapper.selectList(
                        new LambdaQueryWrapper<ExamStudent>().eq(ExamStudent::getStudentUserId, studentUserId))
                .stream().map(ExamStudent::getExamId).distinct().toList();
        List<Exam> makeup = makeupExamIds.isEmpty() ? List.of()
                : baseMapper.selectList(new LambdaQueryWrapper<Exam>()
                        .in(Exam::getId, makeupExamIds)
                        .orderByAsc(Exam::getExamDate));
        List<Exam> all = new ArrayList<>(regular.size() + makeup.size());
        all.addAll(regular);
        all.addAll(makeup);
        return toViews(all);
    }

    @Override
    public List<ClassCourseOptionDto> listClassCourseOptions(Long classId) {
        ClassName cls = classNameMapper.selectById(classId);
        if (cls == null) {
            throw new BusinessException(404, "班级不存在");
        }
        // FIND_IN_SET 命中合班：teach_info.class_name 含该班级即返回
        List<TeachInfo> list = teachInfoMapper.selectList(
                new LambdaQueryWrapper<TeachInfo>()
                        .apply("FIND_IN_SET({0}, class_name) > 0", cls.getClassName()));
        if (list.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = list.stream().map(TeachInfo::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = list.stream().map(TeachInfo::getTeacherId).filter(Objects::nonNull).distinct().toList();
        List<Long> semesterIds = list.stream().map(TeachInfo::getSemesterId).filter(Objects::nonNull).distinct().toList();

        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectByIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.selectByIds(teacherIds).stream()
                        .collect(Collectors.toMap(Teacher::getId, t -> t, (a, b) -> a));
        Map<Long, String> semesterNameMap = semesterIds.isEmpty() ? Map.of()
                : semesterService.listByIds(semesterIds).stream()
                        .collect(Collectors.toMap(Semester::getId, Semester::getName, (a, b) -> a));

        List<Long> teacherUserIds = teacherMap.values().stream().map(Teacher::getUserId).toList();
        Map<Long, String> userNameMap = teacherUserIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(teacherUserIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        return list.stream().map(info -> {
            ClassCourseOptionDto dto = new ClassCourseOptionDto();
            dto.setTeachInfoId(info.getId());
            dto.setCourseId(info.getCourseId());
            dto.setCourseName(courseNameMap.get(info.getCourseId()));
            Teacher t = teacherMap.get(info.getTeacherId());
            if (t != null) {
                dto.setTeacherName(userNameMap.get(t.getUserId()));
            }
            dto.setClassName(info.getClassName());
            dto.setSemesterId(info.getSemesterId());
            dto.setSemesterName(semesterNameMap.get(info.getSemesterId()));
            return dto;
        }).toList();
    }

    // ==================== 补考/重修 ====================

    @Override
    public List<MakeupCandidateDto> listMakeupCandidates(Long courseId, Long semesterId) {
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getCourseId, courseId)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR)
                .lt(Score::getTotalScore, BigDecimal.valueOf(60))
                .orderByAsc(Score::getStudentUserId);
        if (semesterId != null) {
            w.eq(Score::getSemesterId, semesterId);
        }
        List<Score> failing = scoreMapper.selectList(w);
        if (failing.isEmpty()) {
            return List.of();
        }
        List<Long> studentUserIds = failing.stream().map(Score::getStudentUserId).distinct().toList();
        Map<Long, String> nameMap = userMapper.selectByIds(studentUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        Map<Long, String> noMap = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getUserId, studentUserIds)).stream()
                .collect(Collectors.toMap(Student::getUserId, Student::getStudentNo, (a, b) -> a));
        return failing.stream().map(g -> {
            MakeupCandidateDto dto = new MakeupCandidateDto();
            dto.setStudentUserId(g.getStudentUserId());
            dto.setStudentName(nameMap.get(g.getStudentUserId()));
            dto.setStudentNo(noMap.get(g.getStudentUserId()));
            dto.setScoreId(g.getId());
            dto.setTotalScore(g.getTotalScore());
            dto.setScoreLevel(g.getScoreLevel());
            dto.setSemesterId(g.getSemesterId());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public ExamView createMakeupExam(MakeupExamCreateRequest request, Long userId) {
        if (request.getExamType() != ExamTypeEnum.MAKEUP && request.getExamType() != ExamTypeEnum.RETAKE) {
            throw new BusinessException(400, "仅支持补考/重修类型");
        }
        validateMakeup(request);
        Exam e = new Exam();
        e.setExamName(request.getExamName());
        e.setCourseId(request.getCourseId());
        e.setTeachInfoId(null); // 补考/重修不绑授课安排
        e.setExamType(request.getExamType());
        e.setSemesterId(request.getSemesterId());
        e.setExamDate(request.getExamDate());
        e.setStartTime(request.getStartTime());
        e.setEndTime(request.getStartTime().plusMinutes(request.getDurationMinutes()));
        e.setLocalId(request.getLocalId());
        e.setNotes(request.getNotes());
        e.setStatus(ExamStatusEnum.SCHEDULED);
        e.setCreateUserId(userId);
        baseMapper.insert(e);

        // 按不及格名单自动生成考生
        Long sourceSem = request.getSourceSemesterId() != null ? request.getSourceSemesterId() : request.getSemesterId();
        List<Long> studentUserIds = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .eq(Score::getCourseId, request.getCourseId())
                        .eq(Score::getSemesterId, sourceSem)
                        .eq(Score::getScoreType, ScoreTypeEnum.REGULAR)
                        .lt(Score::getTotalScore, BigDecimal.valueOf(60)))
                .stream().map(Score::getStudentUserId).distinct().toList();
        for (Long uid : studentUserIds) {
            ExamStudent es = new ExamStudent();
            es.setExamId(e.getId());
            es.setStudentUserId(uid);
            examStudentMapper.insert(es);
        }
        return toViews(List.of(e)).getFirst();
    }

    @Override
    public List<ExamView> listMakeupExams(Long semesterId) {
        LambdaQueryWrapper<Exam> w = new LambdaQueryWrapper<Exam>()
                .in(Exam::getExamType, ExamTypeEnum.MAKEUP, ExamTypeEnum.RETAKE)
                .orderByDesc(Exam::getExamDate);
        if (semesterId != null) {
            w.eq(Exam::getSemesterId, semesterId);
        }
        return toViews(baseMapper.selectList(w));
    }

    private void validateMakeup(MakeupExamCreateRequest req) {
        if (req.getExamName() == null || req.getExamName().isBlank()) {
            throw new BusinessException(400, "考试名称不能为空");
        }
        if (req.getCourseId() == null) {
            throw new BusinessException(400, "课程不能为空");
        }
        if (req.getSemesterId() == null) {
            throw new BusinessException(400, "学期不能为空");
        }
        if (req.getExamDate() == null) {
            throw new BusinessException(400, "考试日期不能为空");
        }
        if (req.getStartTime() == null || req.getDurationMinutes() == null) {
            throw new BusinessException(400, "开始时间与考试时长不能为空");
        }
        if (req.getDurationMinutes() <= 0) {
            throw new BusinessException(400, "考试时长必须大于0分钟");
        }
        if (!req.getStartTime().plusMinutes(req.getDurationMinutes()).isAfter(req.getStartTime())) {
            throw new BusinessException(400, "考试时长超出当日范围");
        }
    }

    // ==================== 富化与校验 ====================

    private List<ExamView> toViews(List<Exam> exams) {
        if (exams.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = exams.stream().map(Exam::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> localIds = exams.stream().map(Exam::getLocalId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectByIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
        Map<Long, Local> localMap = localIds.isEmpty() ? Map.of()
                : localMapper.selectByIds(localIds).stream()
                        .collect(Collectors.toMap(Local::getId, l -> l, (a, b) -> a));
        return exams.stream().map(e -> {
            ExamView v = new ExamView();
            v.setId(e.getId());
            v.setExamName(e.getExamName());
            v.setCourseId(e.getCourseId());
            v.setCourseName(courseNameMap.get(e.getCourseId()));
            v.setTeachInfoId(e.getTeachInfoId());
            v.setClassName(e.getClassName());
            v.setExamType(e.getExamType());
            v.setSemesterId(e.getSemesterId());
            v.setExamDate(e.getExamDate());
            v.setStartTime(e.getStartTime());
            v.setEndTime(e.getEndTime());
            v.setLocalId(e.getLocalId());
            Local loc = localMap.get(e.getLocalId());
            if (loc != null) {
                String building = loc.getBuilding() != null ? loc.getBuilding() : "";
                String room = loc.getClassRoom() != null ? loc.getClassRoom() : "";
                v.setLocalName((building + " " + room).strip());
            }
            v.setNotes(e.getNotes());
            v.setStatus(e.getStatus());
            v.setCreateTime(e.getCreateTime());
            return v;
        }).toList();
    }

    private void validate(ExamCreateRequest req) {
        if (req.getExamName() == null || req.getExamName().isBlank()) {
            throw new BusinessException(400, "考试名称不能为空");
        }
        if (req.getCourseId() == null) {
            throw new BusinessException(400, "课程不能为空");
        }
        if (req.getExamType() == null) {
            throw new BusinessException(400, "考试类型不能为空");
        }
        if ((req.getExamType() == ExamTypeEnum.FINAL || req.getExamType() == ExamTypeEnum.MIDTERM)
                && (req.getClassName() == null || req.getClassName().isBlank())) {
            throw new BusinessException(400, "期末/期中考试必须指定排考班级");
        }
        if (req.getSemesterId() == null) {
            throw new BusinessException(400, "学期不能为空");
        }
        if (req.getExamDate() == null) {
            throw new BusinessException(400, "考试日期不能为空");
        }
        if (req.getStartTime() == null || req.getDurationMinutes() == null) {
            throw new BusinessException(400, "开始时间与考试时长不能为空");
        }
        if (req.getDurationMinutes() <= 0) {
            throw new BusinessException(400, "考试时长必须大于0分钟");
        }
        if (!req.getStartTime().plusMinutes(req.getDurationMinutes()).isAfter(req.getStartTime())) {
            throw new BusinessException(400, "考试时长超出当日范围");
        }
    }

    private void copy(ExamCreateRequest req, Exam e) {
        e.setExamName(req.getExamName());
        e.setCourseId(req.getCourseId());
        e.setTeachInfoId(req.getTeachInfoId());
        e.setClassName(req.getClassName());
        e.setExamType(req.getExamType());
        e.setSemesterId(req.getSemesterId());
        e.setExamDate(req.getExamDate());
        e.setStartTime(req.getStartTime());
        e.setEndTime(req.getStartTime().plusMinutes(req.getDurationMinutes()));
        e.setLocalId(req.getLocalId());
        e.setNotes(req.getNotes());
    }

    /** 解析学生所在班级名（Student.classId -> ClassName.className）。 */
    private String resolveStudentClassName(Long studentUserId) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (student == null || student.getClassId() == null) {
            return null;
        }
        ClassName cls = classNameMapper.selectById(student.getClassId());
        return cls != null ? cls.getClassName() : null;
    }
}
