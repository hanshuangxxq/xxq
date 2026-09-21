package com.xrq.xxq.module.coursework.announcement.controller;

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
import com.xrq.xxq.module.coursework.announcement.dto.AnnouncementSaveRequest;
import com.xrq.xxq.module.coursework.announcement.dto.AnnouncementView;
import com.xrq.xxq.module.coursework.announcement.service.AnnouncementService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 课程公告端点（纯 JSON）。 */
@RestController
@RequestMapping("/api/coursework/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireTeacher
    public Result<AnnouncementView> create(HttpServletRequest request,
                                           @RequestBody AnnouncementSaveRequest body) {
        return Result.ok(announcementService.create(authFacade.currentUserId(request), body));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<AnnouncementView> update(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody AnnouncementSaveRequest body) {
        return Result.ok(announcementService.update(authFacade.currentUserId(request), id, body));
    }

    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        announcementService.delete(authFacade.currentUserId(request), id);
        return Result.ok();
    }

    @GetMapping
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<List<AnnouncementView>> list(HttpServletRequest request, @RequestParam Long teachInfoId) {
        Long userId = authFacade.currentUserId(request);
        if (AuthFacade.USER_TYPE_TEACHER.equals(authFacade.currentUserType(request))) {
            return Result.ok(announcementService.listForTeacher(userId, teachInfoId));
        }
        return Result.ok(announcementService.listForStudent(userId, teachInfoId));
    }
}
