package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.service.MajorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专业管理接口。仅教务管理员可操作。
 */
@RestController
@RequestMapping("/api/majors")
@RequiredArgsConstructor
public class MajorController {

    private final MajorService majorService;

    @GetMapping
    public Result<List<Major>> list() {
        return Result.ok(majorService.list());
    }

    @PostMapping
    public Result<Major> create(HttpServletRequest request, @RequestBody Major major) {
        checkAcademicAdmin(request);
        majorService.save(major);
        return Result.ok(major);
    }

    @PutMapping("/{id}")
    public Result<Major> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Major major) {
        checkAcademicAdmin(request);
        major.setId(id);
        majorService.updateById(major);
        return Result.ok(major);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        checkAcademicAdmin(request);
        majorService.removeById(id);
        return Result.ok();
    }

    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可操作专业数据");
        }
    }
}
