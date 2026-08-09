package com.xrq.xxq.module.practice.internship.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.internship.dto.TrainingCreateRequest;
import com.xrq.xxq.module.practice.internship.dto.TrainingEnrollmentResponse;
import com.xrq.xxq.module.practice.internship.dto.TrainingResponse;
import com.xrq.xxq.module.practice.internship.dto.TrainingUpdateRequest;
import com.xrq.xxq.module.practice.internship.entity.EnrollStatusEnum;
import com.xrq.xxq.module.practice.internship.entity.TrainingCourse;
import com.xrq.xxq.module.practice.internship.entity.TrainingEnrollment;
import com.xrq.xxq.module.practice.internship.entity.TrainingStatusEnum;
import com.xrq.xxq.module.practice.internship.mapper.TrainingCourseMapper;
import com.xrq.xxq.module.practice.internship.mapper.TrainingEnrollmentMapper;
import com.xrq.xxq.module.practice.internship.service.TrainingService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrainingServiceImpl
        extends ServiceImpl<TrainingCourseMapper, TrainingCourse>
        implements TrainingService {

    private final TrainingEnrollmentMapper enrollmentMapper;
    private final UserMapper userMapper;
    private final SemesterService semesterService;

    @Override
    @Transactional
    public TrainingResponse createCourse(Long creatorUserId, String userType, TrainingCreateRequest request) {
        ParamValidator.requireNonBlank(request.getTitle(), "培训标题");
        ParamValidator.requirePositive(request.getCapacity(), "培训容量");
        TrainingCourse course = new TrainingCourse();
        course.setSemesterId(semesterService.resolveOrDefault(request.getSemesterId()));
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setTeacherId(request.getTeacherId() != null ? request.getTeacherId() : creatorUserId);
        course.setStartTime(request.getStartTime());
        course.setEndTime(request.getEndTime());
        course.setCapacity(request.getCapacity());
        course.setEnrolledCount(0);
        course.setStatus(TrainingStatusEnum.DRAFT);
        save(course);
        return toResponse(course, nameOf(course.getTeacherId()));
    }

    @Override
    @Transactional
    public TrainingResponse updateCourse(Long id, TrainingUpdateRequest request, Long operatorUserId, String userType) {
        TrainingCourse course = requireOwned(id, operatorUserId, userType);
        if (request.getTitle() != null) {
            ParamValidator.requireNonBlank(request.getTitle(), "培训标题");
            course.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }
        if (request.getTeacherId() != null) {
            course.setTeacherId(request.getTeacherId());
        }
        if (request.getStartTime() != null) {
            course.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            course.setEndTime(request.getEndTime());
        }
        if (request.getCapacity() != null) {
            ParamValidator.requirePositive(request.getCapacity(), "培训容量");
            int enrolled = course.getEnrolledCount() == null ? 0 : course.getEnrolledCount();
            if (request.getCapacity() < enrolled) {
                throw new BusinessException(409, "容量不能小于已报名人数");
            }
            course.setCapacity(request.getCapacity());
        }
        updateById(course);
        return toResponse(course, nameOf(course.getTeacherId()));
    }

    @Override
    @Transactional
    public void changeCourseStatus(Long id, TrainingStatusEnum status, Long operatorUserId, String userType) {
        if (status == null) {
            throw new BusinessException(400, "状态不能为空");
        }
        TrainingCourse course = requireOwned(id, operatorUserId, userType);
        course.setStatus(status);
        updateById(course);
    }

    @Override
    public PageResult<TrainingResponse> listCourses(Long operatorUserId, String userType,
                                                    Long teacherId, TrainingStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<TrainingCourse> wrapper = new LambdaQueryWrapper<TrainingCourse>()
                .orderByDesc(TrainingCourse::getId);
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            wrapper.eq(TrainingCourse::getTeacherId, operatorUserId);
        } else if (teacherId != null) {
            wrapper.eq(TrainingCourse::getTeacherId, teacherId);
        }
        if (status != null) {
            wrapper.eq(TrainingCourse::getStatus, status);
        }
        Page<TrainingCourse> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<TrainingCourse> list = page.getRecords();
        if (list.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        Map<Long, String> nameMap = userMapper.toNameMap(
                list.stream().map(TrainingCourse::getTeacherId).distinct().toList());
        List<TrainingResponse> records = list.stream()
                .map(c -> toResponse(c, nameMap.get(c.getTeacherId()))).toList();
        return PageResult.of(page, records);
    }

    @Override
    public TrainingResponse getCourse(Long id) {
        TrainingCourse course = baseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "培训课程不存在");
        }
        return toResponse(course, nameOf(course.getTeacherId()));
    }

    @Override
    public List<TrainingResponse> listAvailableCourses(Long studentUserId) {
        List<TrainingCourse> list = baseMapper.selectList(new LambdaQueryWrapper<TrainingCourse>()
                .eq(TrainingCourse::getStatus, TrainingStatusEnum.OPEN)
                .orderByDesc(TrainingCourse::getId));
        Map<Long, String> nameMap = userMapper.toNameMap(
                list.stream().map(TrainingCourse::getTeacherId).distinct().toList());
        return list.stream().map(c -> toResponse(c, nameMap.get(c.getTeacherId()))).toList();
    }

    @Override
    @Transactional
    public TrainingEnrollmentResponse enroll(Long studentUserId, Long courseId) {
        ParamValidator.requireNonNull(courseId, "培训课程");
        TrainingCourse course = baseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "培训课程不存在");
        }
        if (course.getStatus() != TrainingStatusEnum.OPEN) {
            throw new BusinessException(409, "培训课程未开放");
        }
        Long dup = enrollmentMapper.selectCount(new LambdaQueryWrapper<TrainingEnrollment>()
                .eq(TrainingEnrollment::getCourseId, courseId)
                .eq(TrainingEnrollment::getStudentId, studentUserId)
                .eq(TrainingEnrollment::getStatus, EnrollStatusEnum.ENROLLED));
        if (dup > 0) {
            throw new BusinessException(409, "已报名该培训");
        }
        int affected = baseMapper.update(null, new LambdaUpdateWrapper<TrainingCourse>()
                .eq(TrainingCourse::getId, courseId)
                .lt(TrainingCourse::getEnrolledCount, course.getCapacity())
                .setSql("enrolled_count = enrolled_count + 1"));
        if (affected == 0) {
            throw new BusinessException(409, "培训已满");
        }
        TrainingEnrollment enrollment = new TrainingEnrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentUserId);
        enrollment.setEnrollTime(LocalDateTime.now());
        enrollment.setStatus(EnrollStatusEnum.ENROLLED);
        enrollmentMapper.insert(enrollment);
        return toEnrollResponse(enrollment, course.getTitle(), nameOf(studentUserId));
    }

    @Override
    @Transactional
    public void cancelEnroll(Long studentUserId, Long enrollmentId) {
        TrainingEnrollment enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (!enrollment.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (enrollment.getStatus() != EnrollStatusEnum.ENROLLED) {
            throw new BusinessException(409, "该报名已取消");
        }
        enrollmentMapper.deleteById(enrollmentId);
        baseMapper.update(null, new LambdaUpdateWrapper<TrainingCourse>()
                .eq(TrainingCourse::getId, enrollment.getCourseId())
                .setSql("enrolled_count = GREATEST(enrolled_count - 1, 0)"));
    }

    @Override
    public List<TrainingEnrollmentResponse> listMyEnrollments(Long studentUserId) {
        List<TrainingEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<TrainingEnrollment>()
                        .eq(TrainingEnrollment::getStudentId, studentUserId)
                        .orderByDesc(TrainingEnrollment::getId));
        return toEnrollResponses(enrollments);
    }

    @Override
    public PageResult<TrainingEnrollmentResponse> listEnrollmentsByCourse(Long courseId, Long operatorUserId,
                                                                          String userType, PageQuery pageQuery) {
        requireOwned(courseId, operatorUserId, userType);
        Page<TrainingEnrollment> page = enrollmentMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<TrainingEnrollment>()
                        .eq(TrainingEnrollment::getCourseId, courseId)
                        .orderByDesc(TrainingEnrollment::getId));
        return PageResult.of(page, toEnrollResponses(page.getRecords()));
    }

    @Override
    @Transactional
    public void deleteCourse(Long id, Long operatorUserId, String userType) {
        requireOwned(id, operatorUserId, userType);
        Long active = enrollmentMapper.selectCount(new LambdaQueryWrapper<TrainingEnrollment>()
                .eq(TrainingEnrollment::getCourseId, id)
                .eq(TrainingEnrollment::getStatus, EnrollStatusEnum.ENROLLED));
        if (active > 0) {
            throw new BusinessException(409, "存在已报名学生，不可删除");
        }
        removeById(id);
    }

    // ---- helpers ----

    private TrainingCourse requireOwned(Long id, Long operatorUserId, String userType) {
        TrainingCourse course = baseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "培训课程不存在");
        }
        ensureOwns(course, operatorUserId, userType);
        return course;
    }

    private void ensureOwns(TrainingCourse course, Long operatorUserId, String userType) {
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)
                && !course.getTeacherId().equals(operatorUserId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    private List<TrainingEnrollmentResponse> toEnrollResponses(List<TrainingEnrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = enrollments.stream().map(TrainingEnrollment::getCourseId).distinct().toList();
        Map<Long, String> titleMap = baseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(TrainingCourse::getId, TrainingCourse::getTitle, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        enrollments.forEach(e -> personIds.add(e.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return enrollments.stream()
                .map(e -> toEnrollResponse(e, titleMap.get(e.getCourseId()), nameMap.get(e.getStudentId())))
                .toList();
    }

    private TrainingResponse toResponse(TrainingCourse course, String teacherName) {
        TrainingResponse resp = new TrainingResponse();
        resp.setId(course.getId());
        resp.setSemesterId(course.getSemesterId());
        resp.setTitle(course.getTitle());
        resp.setDescription(course.getDescription());
        resp.setTeacherId(course.getTeacherId());
        resp.setTeacherName(teacherName);
        resp.setStartTime(course.getStartTime());
        resp.setEndTime(course.getEndTime());
        resp.setCapacity(course.getCapacity());
        resp.setEnrolledCount(course.getEnrolledCount());
        resp.setStatus(course.getStatus());
        resp.setCreateTime(course.getCreateTime());
        return resp;
    }

    private TrainingEnrollmentResponse toEnrollResponse(TrainingEnrollment enrollment, String courseTitle, String studentName) {
        TrainingEnrollmentResponse resp = new TrainingEnrollmentResponse();
        resp.setId(enrollment.getId());
        resp.setCourseId(enrollment.getCourseId());
        resp.setCourseTitle(courseTitle);
        resp.setStudentId(enrollment.getStudentId());
        resp.setStudentName(studentName);
        resp.setEnrollTime(enrollment.getEnrollTime());
        resp.setStatus(enrollment.getStatus());
        return resp;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
