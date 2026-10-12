package com.xrq.xxq.module.frontlog.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.common.aop.WebRequestUtils;
import com.xrq.xxq.module.frontlog.dto.FrontendLogItem;
import com.xrq.xxq.module.frontlog.dto.FrontendLogReportRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 前端日志接收：免登录白名单接口（登录页的报错也要能上报），批量接收后经
 * frontend-log 专用 logger 落盘 logs/yyyy/MM/dd/frontend.*.log（见 logback-spring.xml）。
 * <p>
 * 安全权衡：userId/userType 以客户端上报值为准（可伪造）—— 诊断日志场景可接受，
 * 滥用由 RateLimitAspect 日志上报档（按 IP）兜底。所有客户端可控文本字段（type、
 * userType、页面、消息、堆栈、面包屑、客户端环境）服务端强制截断、换行转义，
 * 一条日志一行（grep 友好），防超长字段与换行注入伪造日志行。
 */
@RestController
@RequestMapping("/api/front-logs")
@RequiredArgsConstructor
public class FrontendLogController {

    /** 前端日志专用 logger：additivity=false，只进 frontend 日志文件。 */
    private static final Logger frontLog = LoggerFactory.getLogger("frontend-log");

    private static final int BATCH_MAX = 20;
    private static final int MESSAGE_MAX = 1000;
    private static final int STACK_MAX = 8000;
    private static final int PATH_MAX = 500;
    private static final int JSON_MAX = 4000;
    private static final int TYPE_MAX = 30;
    private static final int USER_TYPE_MAX = 20;

    private static final DateTimeFormatter OCCUR_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ObjectMapper objectMapper;

    @PostMapping
    public Result<Void> report(@RequestBody FrontendLogReportRequest body, HttpServletRequest request) {
        String ip = WebRequestUtils.clientIp(request);
        List<FrontendLogItem> logs = body.getLogs() == null ? List.of() : body.getLogs();
        logs.stream().limit(BATCH_MAX).forEach(item -> writeOne(item, ip));
        return Result.ok();
    }

    /** 单条落盘：任何一条数据异常不拖垮整批。 */
    private void writeOne(FrontendLogItem item, String ip) {
        try {
            String line = formatLine(item, ip);
            switch (item.getLevel() == null ? "" : item.getLevel()) {
                case "error" -> frontLog.error(line);
                case "warn" -> frontLog.warn(line);
                default -> frontLog.info(line);
            }
        } catch (Exception e) {
            frontLog.warn("前端日志格式化失败: {}", e.toString());
        }
    }

    /** 单行格式：字段以「 | 」分隔，堆栈/面包屑中的换行已转义，grep 按段过滤即可。 */
    private String formatLine(FrontendLogItem item, String ip) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("前端日志 | 类型:").append(escape(truncate(nullToDash(item.getType()), TYPE_MAX)));
        sb.append(" | 用户[").append(item.getUserId() == null
                ? "匿名"
                : "userId=" + item.getUserId() + ", userType="
                        + escape(truncate(nullToDash(item.getUserType()), USER_TYPE_MAX)))
          .append(']');
        sb.append(" | IP:").append(ip);
        sb.append(" | 页面:").append(escape(truncate(nullToDash(item.getPage()), PATH_MAX)));
        if (item.getApi() != null) {
            sb.append(" | API:").append(escape(truncate(toJson(item.getApi()), PATH_MAX)));
        }
        sb.append(" | 消息:").append(escape(truncate(nullToDash(item.getMessage()), MESSAGE_MAX)));
        if (item.getStack() != null && !item.getStack().isBlank()) {
            sb.append(" | 堆栈:").append(escape(truncate(item.getStack(), STACK_MAX)));
        }
        if (item.getBreadcrumbs() != null && !item.getBreadcrumbs().isEmpty()) {
            sb.append(" | 面包屑:").append(escape(truncate(toJson(item.getBreadcrumbs()), JSON_MAX)));
        }
        if (item.getClient() != null) {
            sb.append(" | 客户端:").append(escape(truncate(toJson(item.getClient()), JSON_MAX)));
        }
        if (item.getCount() != null && item.getCount() > 1) {
            sb.append(" | 重复:").append(item.getCount());
        }
        if (item.getOccurTime() != null) {
            sb.append(" | 发生时间:").append(LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(item.getOccurTime()), ZoneId.systemDefault())
                    .format(OCCUR_TIME_FORMAT));
        }
        return sb.toString();
    }

    /** 换行/回车转义为字面量，保证一条日志一行（防日志行注入）。 */
    private static String escape(String text) {
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "...(截断)" : text;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String nullToDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }
}
