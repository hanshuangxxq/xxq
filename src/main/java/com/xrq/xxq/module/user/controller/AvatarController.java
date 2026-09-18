package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.module.user.service.avatar.AvatarService;
import com.xrq.xxq.util.auth.RequireLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
@RequireLogin
public class AvatarController {

    private final AvatarService avatarService;

    /**
     * 头像读取（内联显示，不带 Content-Disposition）。
     * <p>
     * <b>为什么必须带 CSP</b>：白名单允许 {@code .svg}，而 SVG 是唯一能内联执行脚本的图片格式，
     * 这里又以 {@code image/svg+xml} 直接内联返回 —— 一个恶意 SVG 头像即构成存储型 XSS
     * （自己的会话 cookie/接口全暴露，且任何登录用户都能加载到它）。
     * {@code default-src 'none'; sandbox} 让该响应在浏览器里不能加载任何子资源、不能执行脚本。
     * <p>
     * 根治办法是把 {@code .svg} 移出白名单，但那会让已有 SVG 头像失效，需产品确认后再做。
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        var path = avatarService.resolveAvatarFile(filename);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                // Spring 的 HttpHeaders 没有 CSP 常量，用字面量
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .contentType(MediaType.parseMediaType(avatarService.resolveContentType(filename)))
                .body(resource);
    }
}
