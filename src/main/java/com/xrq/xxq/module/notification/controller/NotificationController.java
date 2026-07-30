package com.xrq.xxq.module.notification.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

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
import com.xrq.xxq.module.notification.dto.NotificationResponse;
import com.xrq.xxq.module.notification.dto.SendNotificationRequest;
import com.xrq.xxq.module.notification.dto.UnreadCountResponse;
import com.xrq.xxq.module.notification.service.NotificationService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 站内消息提醒接口。
 * <p>
 * 查询/已读/删除：任意登录用户操作本人消息；发送：仅教务管理员/院系管理员。
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthFacade authFacade;

    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);
        return Result.ok(new UnreadCountResponse(notificationService.unreadCount(userId)));
    }

    @GetMapping("/list")
    public Result<List<NotificationResponse>> list(HttpServletRequest request,
                                                   @RequestParam(required = false) String status) {
        Long userId = authFacade.currentUserId(request);
        return Result.ok(notificationService.listByUser(userId, status));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        notificationService.markRead(userId, id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);
        notificationService.markAllRead(userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        notificationService.removeOwned(userId, id);
        return Result.ok();
    }

    @PostMapping("/send")
    public Result<NotificationResponse> send(HttpServletRequest request, @RequestBody SendNotificationRequest body) {
        authFacade.requireUserTypes(request, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(notificationService.send(body));
    }
}
