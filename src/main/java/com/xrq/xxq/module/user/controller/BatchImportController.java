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
import com.xrq.xxq.util.auth.RequireAcademicAdmin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 教务侧用户批量导入：一次性导入学生与教师。
 * <p>
 * 鉴权为 {@link RequireAcademicAdmin}（教务管理员）。
 * 注意：本类原名为 AdminController（路径 /api/aAdmin），易与「系统管理员」混淆——系统管理员是
 * {@code admin} 表的独立实体（见 {@code entity/user/Admin.java}），当前未接入登录与 HTTP 鉴权链路，
 * 两者不是同一个概念，故此处按实际归属改用教务语义命名。
 */
@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
@RequireAcademicAdmin
public class BatchImportController {

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
