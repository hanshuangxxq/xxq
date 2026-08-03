package com.xrq.xxq.module.analysis.service;

import com.xrq.xxq.module.analysis.dto.LearningProgressDto;

/**
 * 学习进度跟踪服务：派生当前学期各课程完成度（由 teach_info 周次 + exam 状态 + score 有无计算）。
 */
public interface ProgressService {

    /**
     * 获取学生学习进度。
     *
     * @param studentUserId  目标学生 user.id
     * @param callerUserId   调用者 user.id
     * @param callerUserType 调用者类型（学生本人 / 教务 / 院系）
     */
    LearningProgressDto getProgress(Long studentUserId, Long callerUserId, String callerUserType);
}
