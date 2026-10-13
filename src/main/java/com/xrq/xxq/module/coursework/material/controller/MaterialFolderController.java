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
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderRenameRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderView;
import com.xrq.xxq.module.coursework.material.service.MaterialFolderService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 课程资料文件夹端点:建/改名/级联删 + 单层列举(移动选择器逐层懒展开用)。 */
@RestController
@RequestMapping("/api/coursework/material-folders")
@RequiredArgsConstructor
public class MaterialFolderController {

    private final MaterialFolderService folderService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireTeacher
    public Result<MaterialFolderView> create(HttpServletRequest request,
                                             @RequestBody MaterialFolderSaveRequest body) {
        return Result.ok(folderService.create(authFacade.currentUserId(request), body));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<Void> rename(HttpServletRequest request, @PathVariable Long id,
                               @RequestBody MaterialFolderRenameRequest body) {
        folderService.rename(authFacade.currentUserId(request), id, body);
        return Result.ok();
    }

    /** 删文件夹:级联软删整棵子树(后代文件夹 + 其中资料行;文件本体交回收任务)。 */
    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        folderService.deleteCascade(authFacade.currentUserId(request), id);
        return Result.ok();
    }

    /** 单层子文件夹(parentId 缺省 = 根层;教师查归属 / 学生查可见)。 */
    @GetMapping
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<List<MaterialFolderView>> level(HttpServletRequest request,
                                                  @RequestParam Long teachInfoId,
                                                  @RequestParam(required = false) Long parentId) {
        return Result.ok(folderService.listLevel(authFacade.currentUserId(request),
                authFacade.currentUserType(request), teachInfoId, parentId));
    }
}
