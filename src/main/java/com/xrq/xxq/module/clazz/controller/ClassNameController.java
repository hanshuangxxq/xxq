package com.xrq.xxq.module.clazz.controller;

import java.util.List;
import java.util.Objects;

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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireDepartment;
import com.xrq.xxq.util.auth.RequireLogin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 班级管理接口。
 * <p>
 * 权限：
 * <ul>
 *   <li>院系管理者（department）- 可查看本院系班级，不能新增/修改/删除</li>
 *   <li>教务管理员（academic_admin）- 全校班级增删改查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/class-names")
@RequiredArgsConstructor
public class ClassNameController {

    private final ClassNameService classNameService;
    private final DepartmentMapper departmentMapper;
    private final MajorMapper majorMapper;
    private final ReferenceValidator referenceValidator;
    private final AuthFacade authFacade;

    /**
     * 查询班级列表。
     * <ul>
     *   <li>院系管理者 - 仅返回本院系班级</li>
     *   <li>教务管理员 - 返回全校班级</li>
     * </ul>
     *
     * @param hasMajor 传 true 时只返回已挂专业的班级。学生的专业与院系都由
     *                 class_name.major_id 推导，未挂专业的班不能被学生归属，因此
     *                 「选班级」类下拉必须排除它们。这类过滤必须在服务端完成：
     *                 客户端按页过滤会与服务端的 total/pages 脱节，出现空白页。
     */
    @GetMapping
    @RequireLogin
    public Result<PageResult<ClassName>> list(HttpServletRequest request,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer pageSize,
                                              @RequestParam(required = false) Boolean hasMajor) {
        String userType = authFacade.currentUserType(request);
        PageQuery pageQuery = new PageQuery(page, pageSize);
        LambdaQueryWrapper<ClassName> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(hasMajor)) {
            wrapper.isNotNull(ClassName::getMajorId);
        }
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = resolveDepartment(request);
            List<Long> classIds = classNameService.classIdsByCollegeId(dept.getCollegeId());
            if (classIds.isEmpty()) {
                // 本院系专业下暂无班级：空集合会让 IN 生成非法 SQL，直接回空页
                return Result.ok(new PageResult<>(List.of(), 0L,
                        pageQuery.resolvedPage(), pageQuery.resolvedSize(), 0L));
            }
            wrapper.in(ClassName::getId, classIds);
        }
        Page<ClassName> result = classNameService.page(pageQuery.toPage(), wrapper);
        return Result.ok(PageResult.of(result, result.getRecords()));
    }

    /** 查询本院系的班级。仅院系管理者可用。 */
    @GetMapping("/department")
    @RequireDepartment
    public Result<List<ClassName>> listByDepartment(HttpServletRequest request) {
        Department dept = resolveDepartment(request);
        return Result.ok(classNameService.listByIds(classNameService.classIdsByCollegeId(dept.getCollegeId())));
    }

    @GetMapping("/{id}")
    @RequireLogin
    public Result<ClassName> getById(HttpServletRequest request, @PathVariable Long id) {
        ClassName className = classNameService.getById(id);
        if (className == null) {
            return Result.fail(404, "班级不存在");
        }

        String userType = authFacade.currentUserType(request);
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = resolveDepartment(request);
            // 班级未挂专业 / 专业未挂院系时 collegeIdOf 返回 null，equals 为 false ⇒ 拒绝访问
            if (!Objects.equals(dept.getCollegeId(), classNameService.collegeIdOf(className.getId()))) {
                throw new BusinessException(403, "无权查看其他院系的班级");
            }
        }

        return Result.ok(className);
    }

    @PostMapping
    @RequireAcademicAdmin
    public Result<ClassName> create(HttpServletRequest request, @RequestBody ClassName className) {
        // 专业必填：学生的专业与院系都经 class_name.major_id 推导，没有专业的班级会让
        // 其下学生两项归 NULL，并在院系视图中彻底消失
        ParamValidator.requireNonNull(className.getMajorId(), "所属专业");
        referenceValidator.requireExists(majorMapper, className.getMajorId(), "专业");
        classNameService.save(className);
        return Result.ok(className);
    }

    @PutMapping("/{id}")
    @RequireAcademicAdmin
    public Result<ClassName> update(HttpServletRequest request, @PathVariable Long id, @RequestBody ClassName className) {
        // majorId 传 null 时 MyBatis Plus 按 NOT_NULL 策略忽略该字段，即保持原专业不变；
        // 传了值则必须存在，否则该班学生立即失去专业与院系
        if (className.getMajorId() != null) {
            referenceValidator.requireExists(majorMapper, className.getMajorId(), "专业");
        }
        className.setId(id);
        classNameService.updateById(className);
        return Result.ok(className);
    }

    @DeleteMapping("/{id}")
    @RequireAcademicAdmin
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        classNameService.deleteById(id);
        return Result.ok();
    }

    /**
     * 解析当前登录的院系管理者所属院系，校验身份并确保院系记录存在。
     *
     * @throws BusinessException(403) 非院系管理者或院系记录不存在
     */
    private Department resolveDepartment(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);

        Department dept = departmentMapper.findByUserId(userId);
        if (dept == null) {
            throw new BusinessException(403, "未找到您的院系信息");
        }
        return dept;
    }
}
