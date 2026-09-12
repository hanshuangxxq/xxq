package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.service.MajorService;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;
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
    @RequireAuth()
    public Result<List<Major>> list() {
        return Result.ok(majorService.list());
    }

    @PostMapping
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<Major> create(HttpServletRequest request, @RequestBody Major major) {
        majorService.save(major);
        return Result.ok(major);
    }

    @PutMapping("/{id}")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<Major> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Major major) {
        major.setId(id);
        majorService.updateById(major);
        return Result.ok(major);
    }

    @DeleteMapping("/{id}")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        majorService.removeById(id);
        return Result.ok();
    }
}
