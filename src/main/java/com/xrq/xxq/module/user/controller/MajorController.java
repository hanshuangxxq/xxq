package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.service.MajorService;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireLogin;
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
    @RequireLogin
    public Result<List<Major>> list() {
        return Result.ok(majorService.list());
    }

    @PostMapping
    @RequireAcademicAdmin
    public Result<Major> create(HttpServletRequest request, @RequestBody Major major) {
        return Result.ok(majorService.create(major));
    }

    @PutMapping("/{id}")
    @RequireAcademicAdmin
    public Result<Major> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Major major) {
        return Result.ok(majorService.update(id, major));
    }

    @DeleteMapping("/{id}")
    @RequireAcademicAdmin
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        majorService.delete(id);
        return Result.ok();
    }
}
