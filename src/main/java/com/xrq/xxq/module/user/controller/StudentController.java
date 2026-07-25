package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.user.dto.StudentDto;
import com.xrq.xxq.module.user.dto.UpdateStudentRequest;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.service.MajorService;
import com.xrq.xxq.module.user.service.StudentService;
import com.xrq.xxq.util.auth.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 学生管理接口。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final ClassNameService classNameService;
    private final MajorService majorService;
    private final AuthFacade authFacade;


    /**
     * 查询学生信息。仅教务管理员可用。
     * 支持按年级、班级、专业、姓名筛选，以及未分班学生查询。
     */
    @GetMapping
    public Result<List<StudentDto>> list(HttpServletRequest request,
                                         @RequestParam(required = false) Long gradeId,
                                         @RequestParam(required = false) String className,
                                         @RequestParam(required = false) String major,
                                         @RequestParam(required = false) Boolean unassigned,
                                         @RequestParam(required = false) String name) {
        authFacade.requireAcademicAdmin(request);

        List<Long> classIds = Collections.emptyList();
        if (className != null && !className.isBlank()) {
            classIds = classNameService
                    .lambdaQuery()
                    .select(ClassName::getId)
                    .like(ClassName::getClassName, className)
                    .list()
                    .stream()
                    .map(ClassName::getId)
                    .toList();
        }
        List<Long> majorIds = Collections.emptyList();
        if (major != null && !major.isBlank()){
            majorIds = majorService
                    .lambdaQuery ()
                    .select(Major::getId)
                    .like (Major::getMajorName, major)
                    .list ()
                    .stream ()
                    .map (Major::getId)
                    .toList ();
        }

        return Result.ok(studentService.queryStudents(gradeId, classIds, majorIds, unassigned, name));
    }

    /** 修改学生信息（学号、班级、专业、入学年份）。仅教务管理员可用。 */
    @PutMapping("/{id}")
    public Result<Boolean> update(HttpServletRequest request,
                                  @PathVariable Long id,
                                  @RequestBody UpdateStudentRequest updateRequest) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(studentService.updateStudentInfo(id, updateRequest));
    }
}
