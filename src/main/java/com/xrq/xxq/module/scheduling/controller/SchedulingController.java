package com.xrq.xxq.module.scheduling.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.scheduling.domain.CourseSchedule;
import com.xrq.xxq.module.scheduling.service.SchedulingService;

import lombok.RequiredArgsConstructor;

/**
 * 排课接口。
 */
@RestController
@RequestMapping("/api/scheduling")
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingService schedulingService;

    /**
     * 触发自动排课求解（异步）。
     * <p>
     * 返回方案ID，前端可通过 {@code GET /api/scheduling/solution/{id}} 轮询结果。
     */
    @PostMapping("/solve")
    public Result<Map<String, Long>> solve() {
        Long scheduleId = schedulingService.solve();
        return Result.ok(Map.of("scheduleId", scheduleId));
    }

    /**
     * 获取排课方案。
     * <p>
     * 返回当前最优解，包含求解状态、得分和每节课的分配结果。
     */
    @GetMapping("/solution/{scheduleId}")
    public Result<CourseSchedule> getSolution(@PathVariable Long scheduleId) {
        CourseSchedule solution = schedulingService.getSolution(scheduleId);
        if (solution == null) {
            return Result.fail(404, "排课方案不存在");
        }
        return Result.ok(solution);
    }

    /**
     * 停止正在进行的排课求解。
     */
    @PostMapping("/stop/{scheduleId}")
    public Result<Void> stopSolving(@PathVariable Long scheduleId) {
        schedulingService.stopSolving(scheduleId);
        return Result.ok();
    }
}
