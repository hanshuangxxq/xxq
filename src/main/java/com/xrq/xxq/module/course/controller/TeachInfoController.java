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

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.entity.TeachInfo;
import com.xrq.xxq.module.course.service.TeachInfoService;
import com.xrq.xxq.module.scheduling.cache.DraftCacheManager;
import com.xrq.xxq.module.scheduling.cache.DraftItem;

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
     * 一个班的课程配置一次性提交，可多次调用追加不同班级。
     */
    @PostMapping("/draft")
    public Result<Integer> addDrafts(@RequestBody List<TeachInfo> drafts) {
        draftCacheManager.addDrafts(drafts);
        return Result.ok(draftCacheManager.size());
    }

    /** 查看当前缓存中的全部草稿（含课程名、教师名）。 */
    @GetMapping("/draft")
    public Result<List<DraftItem>> getDrafts() {
        return Result.ok(draftCacheManager.getAllDrafts());
    }

    /** 查看缓存中已配置的班级汇总（去重）及每个班的课程数。 */
    @GetMapping("/draft/classes")
    public Result<java.util.Map<String, Object>> getDraftClasses() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("classes", draftCacheManager.getClassNames());
        result.put("countByClass", draftCacheManager.countByClass());
        result.put("totalDrafts", draftCacheManager.size());
        return Result.ok(result);
    }

    /** 清空全部草稿。 */
    @DeleteMapping("/draft")
    public Result<Void> clearDrafts() {
        draftCacheManager.clear();
        return Result.ok();
    }

    /** 按班级名称移除草稿（支持重新配置某个班）。 */
    @DeleteMapping("/draft/{className}")
    public Result<Void> removeDraftsByClass(@PathVariable String className) {
        draftCacheManager.removeByClassName(className);
        return Result.ok();
    }
}
