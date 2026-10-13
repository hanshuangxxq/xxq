package com.xrq.xxq.module.coursework.video.controller;

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
import com.xrq.xxq.module.coursework.video.dto.ChapterSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.ChapterTreeView;
import com.xrq.xxq.module.coursework.video.dto.ChapterView;
import com.xrq.xxq.module.coursework.video.dto.NodeRenameRequest;
import com.xrq.xxq.module.coursework.video.service.ChapterNodeService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 章节树（章）端点：章的增删改 + 整棵树查询。节见 SectionController，视频挂载见 VideoController。 */
@RestController
@RequestMapping("/api/coursework/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterNodeService chapterNodeService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireTeacher
    public Result<ChapterView> create(HttpServletRequest request, @RequestBody ChapterSaveRequest body) {
        return Result.ok(chapterNodeService.createChapter(authFacade.currentUserId(request), body));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<Void> rename(HttpServletRequest request, @PathVariable Long id,
                               @RequestBody NodeRenameRequest body) {
        chapterNodeService.renameChapter(authFacade.currentUserId(request), id, body);
        return Result.ok();
    }

    /** 删章：级联软删其下节与视频（文件本体交回收任务）。 */
    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        chapterNodeService.deleteChapter(authFacade.currentUserId(request), id);
        return Result.ok();
    }

    /** 整棵树（章/节/视频;教师查归属 / 学生查可见）。 */
    @GetMapping("/tree")
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<ChapterTreeView> tree(HttpServletRequest request, @RequestParam Long teachInfoId) {
        return Result.ok(chapterNodeService.tree(authFacade.currentUserId(request),
                authFacade.currentUserType(request), teachInfoId));
    }
}
