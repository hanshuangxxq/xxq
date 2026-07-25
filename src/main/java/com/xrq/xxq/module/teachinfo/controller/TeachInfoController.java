package com.xrq.xxq.module.teachinfo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.dto.UserCourseDto;
import com.xrq.xxq.module.course.dto.WeekScheduleDto;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.service.TeachInfoService;
import com.xrq.xxq.module.scheduling.cache.DraftCacheManager;
import com.xrq.xxq.module.scheduling.cache.DraftItem;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.util.auth.AuthFacade;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 教学信息（授课安排）管理接口。
 */
@RestController
@RequestMapping("/api/teach-info")
@RequiredArgsConstructor
public class TeachInfoController {

    private final TeachInfoService teachInfoService;
    private final SemesterService semesterService;
    private final DraftCacheManager draftCacheManager;
    private final DepartmentMapper departmentMapper;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final AuthFacade authFacade;

    @GetMapping
    public Result<UserCourseDto> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer week) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(teachInfoService.listByUserScope(userId, userType, teacherId, courseId, week));
    }

    @GetMapping("/{id}")
    public Result<CourseDto> getById(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        CourseDto resp = teachInfoService.getDetailById(id, userId, userType);
        if (resp == null) {
            return Result.fail(404, "教学信息不存在");
        }
        return Result.ok(resp);
    }

    @GetMapping("/class-courses")
    public Result<List<ClassCourseDto>> listClassCourses(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);
        return Result.ok(teachInfoService.listClassCourses(userId));
    }

    /** 学生查询指定周次的班级课表（走 Redis 缓存）。 */
    @GetMapping("/week-schedule")
    public Result<WeekScheduleDto> getWeekSchedule(HttpServletRequest request,
                                                    @RequestParam Integer week) {
        Long userId = authFacade.requireStudentUserId(request);

        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
        if (student == null || student.getClassId() == null) {
            return Result.fail(404, "未找到您的班级信息");
        }

        ClassName cls = classNameMapper.selectById(student.getClassId());
        if (cls == null) {
            return Result.fail(404, "未找到您的班级信息");
        }

        return Result.ok(teachInfoService.getWeekSchedule(cls.getClassName(), week));
    }

    /** 新增授课安排。合班时 className 用逗号分隔：如 "计科2201,计科2101"。 */
    @PostMapping
    public Result<TeachInfo> create(@RequestBody TeachInfo teachInfo) {
        teachInfoService.save(teachInfo);
        return Result.ok(teachInfo);
    }

    /** 修改授课安排（部分更新用 PUT，排课结果由 scheduling 模块自动写回）。 */
    @PutMapping("/{id}")
    public Result<TeachInfo> update(@PathVariable Long id, @RequestBody TeachInfo teachInfo) {
        teachInfo.setId(id);
        teachInfoService.updateById(teachInfo);
        return Result.ok(teachInfo);
    }

    /** 删除授课安排。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teachInfoService.removeById(id);
        return Result.ok();
    }

    // ──────────────────────── 草稿缓存接口 ────────────────────────

    /**
     * 批量提交授课草稿到缓存（不写库）。
     * 教务管理员和院系管理者可操作。
     */
    @PostMapping("/draft")
    public Result<Integer> addDrafts(HttpServletRequest request, @RequestBody List<TeachInfo> drafts) {
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN,
                AuthFacade.USER_TYPE_DEPARTMENT);
        draftCacheManager.addDrafts(drafts);
        return Result.ok(draftCacheManager.size());
    }

    /**
     * 查看草稿箱。
     * <ul>
     *   <li>教务管理员 - 全校草稿</li>
     *   <li>院系管理者 - 仅本院系草稿</li>
     * </ul>
     */
    @GetMapping("/draft")
    public Result<List<DraftItem>> getDrafts(HttpServletRequest request) {
        String userType = authFacade.currentUserType(request);

        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            return Result.ok(draftCacheManager.getAllDrafts());
        }

        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = resolveDepartment(request);
            return Result.ok(draftCacheManager.getDraftsByCollege(dept.getDepartmentName()));
        }

        return Result.ok(List.of());
    }

    /** 查看缓存中已配置的班级汇总（去重）、每个班的课程数及学期信息。 */
    @GetMapping("/draft/classes")
    public Result<java.util.Map<String, Object>> getDraftClasses(HttpServletRequest request) {
        List<DraftItem> drafts = resolveDraftsByRole(request);
        var result = new java.util.LinkedHashMap<String, Object>();

        // 学期信息：从草稿中提取学期ID并查询学期名称
        Long semesterId = drafts.stream()
                .map(DraftItem::getSemesterId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
        if (semesterId != null) {
            Semester semester = semesterService.getById(semesterId);
            result.put("semesterId", semesterId);
            result.put("semesterName", semester != null ? semester.getName() : null);
        }

        result.put("classes", drafts.stream()
                .map(DraftItem::getClassName)
                .filter(name -> name != null && !name.isBlank())
                .flatMap(name -> {
                    var list = new java.util.ArrayList<String>();
                    for (String part : name.split(",")) {
                        String trimmed = part.strip();
                        if (!trimmed.isEmpty()) {
                            list.add(trimmed);
                        }
                    }
                    return list.stream();
                })
                .distinct()
                .toList());
        result.put("countByClass", drafts.stream()
                .filter(d -> d.getClassName() != null && !d.getClassName().isBlank())
                .flatMap(d -> {
                    var list = new java.util.ArrayList<String>();
                    for (String part : d.getClassName().split(",")) {
                        String trimmed = part.strip();
                        if (!trimmed.isEmpty()) {
                            list.add(trimmed);
                        }
                    }
                    return list.stream();
                })
                .collect(java.util.stream.Collectors.groupingBy(
                        name -> name,
                        java.util.stream.Collectors.counting())));
        result.put("totalDrafts", drafts.size());
        return Result.ok(result);
    }

    /** 清空全部草稿。仅教务管理员可操作。 */
    @DeleteMapping("/draft")
    public Result<Void> clearDrafts(HttpServletRequest request) {
        authFacade.requireAcademicAdmin(request);
        draftCacheManager.clear();
        return Result.ok();
    }

    /**
     * 按唯一键删除单条草稿。
     * 教务管理员可删任意记录；院系管理者仅可删本院系记录。
     */
    @DeleteMapping("/draft/item")
    public Result<Void> removeDraftItem(HttpServletRequest request,
                                        @RequestParam Long courseId,
                                        @RequestParam Long teacherId,
                                        @RequestParam String className) {
        String userType = authFacade.currentUserType(request);

        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = resolveDepartment(request);
            List<DraftItem> deptDrafts = draftCacheManager.getDraftsByCollege(dept.getDepartmentName());
            boolean belongsToDept = deptDrafts.stream()
                    .anyMatch(d -> courseId.equals(d.getCourseId())
                            && teacherId.equals(d.getTeacherId())
                            && className.equals(d.getClassName()));
            if (!belongsToDept) {
                throw new BusinessException(403, "无权删除其他院系的草稿");
            }
        } else {
            authFacade.requireUserTypes(request,
                    AuthFacade.USER_TYPE_ACADEMIC_ADMIN,
                    AuthFacade.USER_TYPE_DEPARTMENT);
        }

        boolean removed = draftCacheManager.removeByKey(courseId, teacherId, className);
        if (!removed) {
            return Result.fail(404, "草稿记录不存在");
        }
        return Result.ok();
    }

    /**
     * 按班级名称移除草稿（批量）。
     * 教务管理员可移除任意班级；院系管理者仅可移除本院系班级。
     */
    @DeleteMapping("/draft/{className}")
    public Result<Void> removeDraftsByClass(HttpServletRequest request, @PathVariable String className) {
        String userType = authFacade.currentUserType(request);

        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = resolveDepartment(request);
            List<DraftItem> deptDrafts = draftCacheManager.getDraftsByCollege(dept.getDepartmentName());
            boolean belongsToDept = deptDrafts.stream()
                    .anyMatch(d -> {
                        String cn = d.getClassName();
                        if (cn == null) return false;
                        for (String part : cn.split(",")) {
                            if (part.strip().equals(className)) {
                                return true;
                            }
                        }
                        return false;
                    });
            if (!belongsToDept) {
                throw new BusinessException(403, "无权移除其他院系的草稿");
            }
        } else {
            authFacade.requireUserTypes(request,
                    AuthFacade.USER_TYPE_ACADEMIC_ADMIN,
                    AuthFacade.USER_TYPE_DEPARTMENT);
        }

        draftCacheManager.removeByClassName(className);
        return Result.ok();
    }

    // ──────────────────────── 权限校验 ────────────────────────

    private List<DraftItem> resolveDraftsByRole(HttpServletRequest request) {
        String userType = authFacade.currentUserType(request);

        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            return draftCacheManager.getAllDrafts();
        }

        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = resolveDepartment(request);
            return draftCacheManager.getDraftsByCollege(dept.getDepartmentName());
        }

        return List.of();
    }

    private Department resolveDepartment(HttpServletRequest request) {
        Long userId = authFacade.requireDepartmentUserId(request);

        Department dept = departmentMapper.selectOne(
                new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
        if (dept == null) {
            throw new BusinessException(403, "未找到您的院系信息");
        }
        return dept;
    }
}
