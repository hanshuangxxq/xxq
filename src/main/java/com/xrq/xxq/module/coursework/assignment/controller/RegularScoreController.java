package com.xrq.xxq.module.coursework.assignment.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.assignment.dto.RegularScoreSyncView;
import com.xrq.xxq.module.coursework.assignment.service.RegularScoreSyncService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireTeacher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 平时分合成端点。 */
@RestController
@RequestMapping("/api/coursework/teach-infos")
@RequiredArgsConstructor
public class RegularScoreController {

    private final RegularScoreSyncService syncService;
    private final AuthFacade authFacade;

    /** 将该授课组已批改作业成绩合成为平时分（覆盖式重算，可重复触发）。 */
    @PostMapping("/{teachInfoId}/regular-score/sync")
    @RequireTeacher
    public Result<RegularScoreSyncView> sync(HttpServletRequest request, @PathVariable Long teachInfoId) {
        return Result.ok(syncService.sync(authFacade.currentUserId(request), teachInfoId));
    }
}
