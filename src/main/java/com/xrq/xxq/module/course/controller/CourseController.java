package com.xrq.xxq.module.course.controller;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

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
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.service.CourseService;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 课程管理接口。
 * <p>
 * 权限：写接口（增/改/删）仅教务管理员（academic_admin）可操作；
 * 查询接口返回 course 表常规课，教务管理者视图额外包含由 selection_campaign 合成的公选课条目
 *（source = SELECTION_CAMPAIGN，id = campaign.id），院系管理者不展示公选课。
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final SelectionCampaignService selectionCampaignService;
    private final AuthFacade authFacade;

    @GetMapping
    public Result<List<Course>> list(HttpServletRequest request) {
        String userType = authFacade.currentUserType(request);
        List<Course> courses = new ArrayList<>(courseService.list());
        // 公选课不在 course 表，教务管理者视图追加由 selection_campaign 合成的公选课条目
        if (!AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            courses.addAll(synthesizePublicCourses());
        }
        return Result.ok(courses);
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id,
                                  @RequestParam(required = false) String source) {
        // source=SELECTION_CAMPAIGN 表示查询公选课合成条目（id 为 campaign.id）
        if ("SELECTION_CAMPAIGN".equals(source)) {
            SelectionCampaign c = selectionCampaignService.getById(id);
            if (c == null) {
                return Result.fail(404, "课程不存在");
            }
            return Result.ok(toCourse(c));
        }
        Course course = courseService.getById(id);
        if (course == null) {
            return Result.fail(404, "课程不存在");
        }
        return Result.ok(course);
    }

    /** 将选课活动合成为 Course 视图条目（不落库，仅供列表展示）。 */
    private List<Course> synthesizePublicCourses() {
        return selectionCampaignService.list().stream()
                .map(this::toCourse)
                .toList();
    }

    private Course toCourse(SelectionCampaign c) {
        Course course = new Course();
        course.setId(c.getId());                         // 合成条目 id = campaign.id
        course.setCourseName(c.getCourseName());
        course.setCourseCode(c.getCourseCode());
        course.setCredit(c.getCredit());
        course.setCourseHour(c.getCourseHour());
        course.setDescription(c.getDescription());
        course.setCourseType(c.getCourseType());
        course.setSource(Course.SOURCE_SELECTION_CAMPAIGN);
        return course;
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
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id,
                               @RequestParam(required = false) String source) {
        authFacade.requireAcademicAdmin(request);
        // source=SELECTION_CAMPAIGN 时 id 为 campaign.id，走选课活动删除（避免误删同 id 的常规课）
        if ("SELECTION_CAMPAIGN".equals(source)) {
            selectionCampaignService.delete(id);
            return Result.ok();
        }
        courseService.removeById(id);
        return Result.ok();
    }
}
