package com.xrq.xxq.module.scheduling.service;

import com.xrq.xxq.module.scheduling.domain.CourseSchedule;

/**
 * 自动排课服务。
 * <p>
 * 从数据库读取授课信息，组装排课问题，调用 Timefold 求解器进行约束求解，
 * 并将结果写回 teach_info 表。
 */
public interface SchedulingService {

    /**
     * 触发排课求解（异步）。
     *
     * @param semesterId 学期ID，为 null 时使用当前学期
     * @return 方案ID，可用于查询求解状态和结果
     */
    Long solve(Long semesterId);

    /**
     * 获取排课方案。
     *
     * @param scheduleId 方案ID（由 {@link #solve()} 返回）
     * @return 排课方案（包含求解状态、得分和已分配的课程列表）
     */
    CourseSchedule getSolution(Long scheduleId);

    /**
     * 停止指定方案的求解。
     *
     * @param scheduleId 方案ID
     */
    void stopSolving(Long scheduleId);
}
