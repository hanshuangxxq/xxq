package com.xrq.xxq.module.coursework.video.controller;

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
import com.xrq.xxq.module.coursework.video.dto.VideoSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoUpdateRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoView;
import com.xrq.xxq.module.coursework.video.service.VideoService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 教学视频端点。播放在 /ws/video/{id}（WS），此处只做元数据管理。 */
@RestController
@RequestMapping("/api/coursework/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final AuthFacade authFacade;

    /** 登记视频（文件先经 file 模块分片上传回传 filePath；小文件也可随请求整传）。 */
    @PostMapping
    @RequireTeacher
    public Result<VideoView> register(HttpServletRequest request,
                                      @RequestPart("data") VideoSaveRequest body,
                                      @RequestPart(value = "file", required = false) MultipartFile file) {
        return Result.ok(videoService.register(authFacade.currentUserId(request), body, file));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<VideoView> update(HttpServletRequest request, @PathVariable Long id,
                                    @RequestBody VideoUpdateRequest body) {
        return Result.ok(videoService.update(authFacade.currentUserId(request), id, body));
    }

    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        videoService.delete(authFacade.currentUserId(request), id);
        return Result.ok();
    }

    @GetMapping
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<List<VideoView>> list(HttpServletRequest request, @RequestParam Long teachInfoId) {
        Long userId = authFacade.currentUserId(request);
        if (AuthFacade.USER_TYPE_TEACHER.equals(authFacade.currentUserType(request))) {
            return Result.ok(videoService.listForTeacher(userId, teachInfoId));
        }
        return Result.ok(videoService.listForStudent(userId, teachInfoId));
    }

    /** 元数据（WS 播放前置：拿到 size/sha256 后再连 WS）。 */
    @GetMapping("/{id}")
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<VideoView> detail(HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(videoService.detail(authFacade.currentUserId(request),
                authFacade.currentUserType(request), id));
    }
}
