package com.xrq.xxq.module.user.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.BatchImportResponse;
import com.xrq.xxq.module.user.dto.UserImportItem;
import com.xrq.xxq.module.user.service.BatchImportService;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/aAdmin")
@RequiredArgsConstructor
@RequireAuth(UserType.ACADEMIC_ADMIN)
public class AdminController {

    private final BatchImportService batchImportService;

    @PostMapping("/batch-import")
    public Result<BatchImportResponse> batchImport(HttpServletRequest request,
                                                    @RequestBody Map<String, List<UserImportItem>> body) {
        List<UserImportItem> users = body.get("users");
        if (users == null || users.isEmpty()) {
            return Result.fail(400, "导入数据不能为空");
        }

        BatchImportResponse result = batchImportService.batchImport(users);
        return Result.ok(result);
    }
}
