package com.xrq.xxq.module.user.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.BatchImportResponse;
import com.xrq.xxq.module.user.dto.UserImportItem;
import com.xrq.xxq.module.user.service.BatchImportService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/aAdmin")
@RequiredArgsConstructor
public class AdminController {

    private final BatchImportService batchImportService;

    @PostMapping("/batch-import")
    public Result<BatchImportResponse> batchImport(HttpServletRequest request,
                                                    @RequestBody Map<String, List<UserImportItem>> body) {
        checkAcademicAdmin(request);

        List<UserImportItem> users = body.get("users");
        if (users == null || users.isEmpty()) {
            return Result.fail(400, "导入数据不能为空");
        }

        BatchImportResponse result = batchImportService.batchImport(users);
        return Result.ok(result);
    }

    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可执行此操作");
        }
    }
}
