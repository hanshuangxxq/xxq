package com.xrq.xxq.module.course.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.service.CourseService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 课程管理接口。
 * <p>
 * 权限：写接口（增/改/删）仅教务管理员（academic_admin）可操作；
 * 院系管理者（department）查询时排除选课活动衍生的课程（source = SELECTION_CAMPAIGN）。
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final AuthFacade authFacade;

    @GetMapping
    public Result<List<Course>> list(HttpServletRequest request) {
        String userType = authFacade.currentUserType(request);
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            List<Course> list = courseService.list(
                    new LambdaQueryWrapper<Course>()
                            .and(w -> w.ne(Course::getSource, Course.SOURCE_SELECTION_CAMPAIGN)
                                    .or().isNull(Course::getSource)));
            return Result.ok(list);
        }
        return Result.ok(courseService.list());
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course == null) {
            return Result.fail(404, "课程不存在");
        }
        return Result.ok(course);
    }

    @PostMapping
    public Result<Course> create(HttpServletRequest request, @RequestBody Course course) {
        authFacade.requireAcademicAdmin(request);
        courseService.save(course);
        return Result.ok(course);
    }

    @PutMapping("/{id}")
    public Result<Course> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Course course) {
        authFacade.requireAcademicAdmin(request);
        course.setId(id);
        courseService.updateById(course);
        return Result.ok(course);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        courseService.removeById(id);
        return Result.ok();
    }
}
