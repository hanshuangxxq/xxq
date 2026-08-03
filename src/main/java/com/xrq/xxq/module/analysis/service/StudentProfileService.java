package com.xrq.xxq.module.analysis.service;

import com.xrq.xxq.module.analysis.dto.StudentProfileDto;

/**
 * 学生个人画像服务：整合成绩、学分、绩点、趋势与排名生成个性化学习画像。
 */
public interface StudentProfileService {

    /**
     * 获取学生画像。
     *
     * @param studentUserId  目标学生 user.id
     * @param callerUserId   调用者 user.id
     * @param callerUserType 调用者类型（学生本人 / 教务 / 院系）
     */
    StudentProfileDto getProfile(Long studentUserId, Long callerUserId, String callerUserType);
}
