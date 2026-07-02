package com.xrq.xxq.module.course.controller;

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
import com.xrq.xxq.module.course.entity.TeachInfo;
import com.xrq.xxq.module.course.service.TeachInfoService;
import com.xrq.xxq.module.scheduling.cache.DraftCacheManager;
import com.xrq.xxq.module.scheduling.cache.DraftItem;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;

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
    private final DraftCacheManager draftCacheManager;
    private final DepartmentMapper departmentMapper;

    @GetMapping
    public Result<List<CourseDto>> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseId) {
        Long userId = (Long) request.getAttribute("userId");
        String userType = (String) request.getAttribute("userType");
        return Result.ok(teachInfoService.listByUserScope(userId, userType, teacherId, courseId));
    }

    @GetMapping("/{id}")
    public Result<CourseDto> getById(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        String userType = (String) request.getAttribute("userType");
        CourseDto resp = teachInfoService.getDetailById(id, userId, userType);
        if (resp == null) {
            return Result.fail(404, "教学信息不存在");
        }
        return Result.ok(resp);
    }

    @GetMapping("/class-courses")
    public Result<List<ClassCourseDto>> listClassCourses(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(teachInfoService.listClassCourses(userId));
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
        checkDraftWritePermission(request);
        draftCacheManager.addDrafts(drafts);
        return Result.ok(draftCacheManager.size());
    }

    /**
     * 查看草稿箱。
     * <ul>
     *   <li>教务管理员 — 全校草稿</li>
     *   <li>院系管理者 — 仅本院系草稿</li>
     * </ul>
     */
    @GetMapping("/draft")
    public Result<List<DraftItem>> getDrafts(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");

        if ("academic_admin".equals(userType)) {
            return Result.ok(draftCacheManager.getAllDrafts());
        }

        if ("department".equals(userType)) {
            Department dept = resolveDepartment(request);
            return Result.ok(draftCacheManager.getDraftsByCollege(dept.getDepartmentName()));
        }

        return Result.ok(List.of());
    }

    /** 查看缓存中已配置的班级汇总（去重）及每个班的课程数。 */
    @GetMapping("/draft/classes")
    public Result<java.util.Map<String, Object>> getDraftClasses(HttpServletRequest request) {
        List<DraftItem> drafts = resolveDraftsByRole(request);
        var result = new java.util.LinkedHashMap<String, Object>();
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
                .collect(java.util.stream.Collectors.groupingBy(
                        DraftItem::getClassName,
                        java.util.stream.Collectors.counting())));
        result.put("totalDrafts", drafts.size());
        return Result.ok(result);
    }

    /** 清空全部草稿。仅教务管理员可操作。 */
    @DeleteMapping("/draft")
    public Result<Void> clearDrafts(HttpServletRequest request) {
        checkAcademicAdmin(request);
        draftCacheManager.clear();
        return Result.ok();
    }

    /**
     * 按班级名称移除草稿。
     * 教务管理员可移除任意班级；院系管理者仅可移除本院系班级。
     */
    @DeleteMapping("/draft/{className}")
    public Result<Void> removeDraftsByClass(HttpServletRequest request, @PathVariable String className) {
        String userType = (String) request.getAttribute("userType");

        if ("department".equals(userType)) {
            Department dept = resolveDepartment(request);
            List<DraftItem> deptDrafts = draftCacheManager.getDraftsByCollege(dept.getDepartmentName());
            boolean belongsToDept = deptDrafts.stream()
                    .anyMatch(d -> className.equals(d.getClassName()));
            if (!belongsToDept) {
                throw new BusinessException(403, "无权移除其他院系的草稿");
            }
        } else {
            checkDraftWritePermission(request);
        }

        draftCacheManager.removeByClassName(className);
        return Result.ok();
    }

    // ──────────────────────── 权限校验 ────────────────────────

    private List<DraftItem> resolveDraftsByRole(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");

        if ("academic_admin".equals(userType)) {
            return draftCacheManager.getAllDrafts();
        }

        if ("department".equals(userType)) {
            Department dept = resolveDepartment(request);
            return draftCacheManager.getDraftsByCollege(dept.getDepartmentName());
        }

        return List.of();
    }

    /** 草稿写操作仅教务管理员和院系管理者可用。 */
    private void checkDraftWritePermission(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType) && !"department".equals(userType)) {
            throw new BusinessException(403, "无草稿箱操作权限");
        }
    }

    /** 仅教务管理员可用。 */
    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可执行此操作");
        }
    }

    private Department resolveDepartment(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String userType = (String) request.getAttribute("userType");

        if (!"department".equals(userType)) {
            throw new BusinessException(403, "仅院系管理者可执行此操作");
        }

        Department dept = departmentMapper.selectOne(
                new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
        if (dept == null) {
            throw new BusinessException(403, "未找到您的院系信息");
        }
        return dept;
    }
}
