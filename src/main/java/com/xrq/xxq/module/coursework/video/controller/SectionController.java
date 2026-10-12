package com.xrq.xxq.module.coursework.video.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.video.dto.NodeRenameRequest;
import com.xrq.xxq.module.coursework.video.dto.SectionSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.SectionView;
import com.xrq.xxq.module.coursework.video.service.ChapterNodeService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireTeacher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 章节树（节）端点：节挂在章下，视频挂节。节不可换父章，仅可改名/删除。 */
@RestController
@RequestMapping("/api/coursework/sections")
@RequiredArgsConstructor
public class SectionController {

    private final ChapterNodeService chapterNodeService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireTeacher
    public Result<SectionView> create(HttpServletRequest request, @RequestBody SectionSaveRequest body) {
        return Result.ok(chapterNodeService.createSection(authFacade.currentUserId(request), body));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<Void> rename(HttpServletRequest request, @PathVariable Long id,
                               @RequestBody NodeRenameRequest body) {
        chapterNodeService.renameSection(authFacade.currentUserId(request), id, body);
        return Result.ok();
    }

    /** 删节：级联软删其下视频（文件本体交回收任务）。 */
    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        chapterNodeService.deleteSection(authFacade.currentUserId(request), id);
        return Result.ok();
    }
}
