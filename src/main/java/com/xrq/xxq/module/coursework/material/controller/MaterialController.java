package com.xrq.xxq.module.coursework.material.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.material.dto.MaterialSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialUpdateRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialView;
import com.xrq.xxq.module.coursework.material.service.MaterialService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 课程资料端点。下载统一走 file 模块 POST 通用下载。 */
@RestController
@RequestMapping("/api/coursework/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireTeacher
    public Result<MaterialView> upload(HttpServletRequest request,
                                       @RequestPart("data") MaterialSaveRequest body,
                                       @RequestPart(value = "file", required = false) MultipartFile file) {
        return Result.ok(materialService.upload(authFacade.currentUserId(request), body, file));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<MaterialView> update(HttpServletRequest request, @PathVariable Long id,
                                       @RequestBody MaterialUpdateRequest body) {
        return Result.ok(materialService.update(authFacade.currentUserId(request), id, body));
    }

    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        materialService.delete(authFacade.currentUserId(request), id);
        return Result.ok();
    }

    @GetMapping
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<List<MaterialView>> list(HttpServletRequest request, @RequestParam Long teachInfoId) {
        Long userId = authFacade.currentUserId(request);
        if (AuthFacade.USER_TYPE_TEACHER.equals(authFacade.currentUserType(request))) {
            return Result.ok(materialService.listForTeacher(userId, teachInfoId));
        }
        return Result.ok(materialService.listForStudent(userId, teachInfoId));
    }
}
