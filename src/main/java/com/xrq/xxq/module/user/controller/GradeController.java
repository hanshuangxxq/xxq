package com.xrq.xxq.module.user.controller;

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
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.service.GradeService;

import lombok.RequiredArgsConstructor;

/**
 * 年级字典管理接口。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService gradeService;
    private final StudentMapper studentMapper;

    @GetMapping
    public Result<List<Grade>> list(HttpServletRequest request) {
        checkAcademicAdmin(request);
        return Result.ok(gradeService.list());
    }

    @GetMapping("/{id}")
    public Result<Grade> getById(HttpServletRequest request, @PathVariable Long id) {
        checkAcademicAdmin(request);
        Grade grade = gradeService.getById(id);
        if (grade == null) {
            throw new BusinessException(404, "年级不存在");
        }
        return Result.ok(grade);
    }

    @PostMapping
    public Result<Grade> create(HttpServletRequest request, @RequestBody Grade grade) {
        checkAcademicAdmin(request);
        if (grade.getName() == null || grade.getName().isBlank()) {
            throw new BusinessException(400, "年级名称不能为空");
        }
        Long exists = gradeService.lambdaQuery().eq(Grade::getName, grade.getName()).count();
        if (exists > 0) {
            throw new BusinessException(409, "年级名称已存在");
        }
        gradeService.save(grade);
        return Result.ok(grade);
    }

    @PutMapping("/{id}")
    public Result<Grade> update(HttpServletRequest request,
                                @PathVariable Long id,
                                @RequestBody Grade grade) {
        checkAcademicAdmin(request);
        Grade exists = gradeService.getById(id);
        if (exists == null) {
            throw new BusinessException(404, "年级不存在");
        }
        if (grade.getName() != null && !grade.getName().equals(exists.getName())) {
            Long conflict = gradeService.lambdaQuery().eq(Grade::getName, grade.getName()).count();
            if (conflict > 0) {
                throw new BusinessException(409, "年级名称已存在");
            }
        }
        grade.setId(id);
        gradeService.updateById(grade);
        return Result.ok(gradeService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        checkAcademicAdmin(request);
        Grade grade = gradeService.getById(id);
        if (grade == null) {
            throw new BusinessException(404, "年级不存在");
        }
        Long refCount = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getGradeId, id));
        if (refCount > 0) {
            throw new BusinessException(409, "该年级仍有学生引用，无法删除");
        }
        gradeService.removeById(id);
        return Result.ok();
    }

    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可操作年级");
        }
    }
}
