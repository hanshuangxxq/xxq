package com.xrq.xxq.module.course.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.service.TeachInfoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teach-info")
@RequiredArgsConstructor
public class TeachInfoController {

    private final TeachInfoService teachInfoService;

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
}
