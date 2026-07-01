package com.xrq.xxq.module.course.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.dto.TeachInfoResponse;
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
    public Result<List<TeachInfoResponse>> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseId) {
        Long userId = (Long) request.getAttribute("userId");
        String userType = (String) request.getAttribute("userType");
        return Result.ok(teachInfoService.listByUserScope(userId, userType, teacherId, courseId));
    }

    @GetMapping("/{id}")
    public Result<TeachInfoResponse> getById(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        String userType = (String) request.getAttribute("userType");
        TeachInfoResponse resp = teachInfoService.getDetailById(id, userId, userType);
        if (resp == null) {
            return Result.fail(404, "教学信息不存在");
        }
        return Result.ok(resp);
    }
}
