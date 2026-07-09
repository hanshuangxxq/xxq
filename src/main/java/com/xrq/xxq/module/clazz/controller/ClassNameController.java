package com.xrq.xxq.module.clazz.controller;

import java.util.List;

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
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 班级管理接口。
 * <p>
 * 权限：
 * <ul>
 *   <li>院系管理者（department）— 可查看本院系班级，不能新增/修改/删除</li>
 *   <li>教务管理员（academic_admin）— 全校班级增删改查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/class-names")
@RequiredArgsConstructor
public class ClassNameController {

    private final ClassNameService classNameService;
    private final DepartmentMapper departmentMapper;

    /**
     * 查询班级列表。
     * <ul>
     *   <li>院系管理者 — 仅返回本院系班级</li>
     *   <li>教务管理员 — 返回全校班级</li>
     * </ul>
     */
    @GetMapping
    public Result<List<ClassName>> list(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");

        if ("department".equals(userType)) {
            Department dept = resolveDepartment(request);
            List<ClassName> list = classNameService.list(
                    new LambdaQueryWrapper<ClassName>().eq(ClassName::getCollege, dept.getDepartmentName()));
            return Result.ok(list);
        }

        return Result.ok(classNameService.list());
    }

    /** 查询本院系的班级。仅院系管理者可用。 */
    @GetMapping("/department")
    public Result<List<ClassName>> listByDepartment(HttpServletRequest request) {
        Department dept = resolveDepartment(request);
        List<ClassName> list = classNameService.list(
                new LambdaQueryWrapper<ClassName>().eq(ClassName::getCollege, dept.getDepartmentName()));
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    public Result<ClassName> getById(HttpServletRequest request, @PathVariable Long id) {
        ClassName className = classNameService.getById(id);
        if (className == null) {
            return Result.fail(404, "班级不存在");
        }

        String userType = (String) request.getAttribute("userType");
        if ("department".equals(userType)) {
            Department dept = resolveDepartment(request);
            if (!dept.getDepartmentName().equals(className.getCollege())) {
                throw new BusinessException(403, "无权查看其他院系的班级");
            }
        }

        return Result.ok(className);
    }

    @PostMapping
    public Result<ClassName> create(HttpServletRequest request, @RequestBody ClassName className) {
        checkAcademicAdmin(request);
        classNameService.save(className);
        return Result.ok(className);
    }

    @PutMapping("/{id}")
    public Result<ClassName> update(HttpServletRequest request, @PathVariable Long id, @RequestBody ClassName className) {
        checkAcademicAdmin(request);
        className.setId(id);
        classNameService.updateById(className);
        return Result.ok(className);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        checkAcademicAdmin(request);
        classNameService.removeById(id);
        return Result.ok();
    }

    // ──────────────────────── 权限校验 ────────────────────────

    /** 仅教务管理员可执行写操作。 */
    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可操作班级数据");
        }
    }

    /**
     * 解析当前登录的院系管理者所属院系，校验身份并确保院系记录存在。
     *
     * @throws BusinessException(403) 非院系管理者或院系记录不存在
     */
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
