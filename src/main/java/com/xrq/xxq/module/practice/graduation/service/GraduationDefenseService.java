package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.xrq.xxq.module.practice.graduation.dto.DefenseArrangeRequest;
import com.xrq.xxq.module.practice.graduation.dto.DefenseResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreConfirmRequest;
import com.xrq.xxq.module.practice.graduation.dto.ScoreResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreSubmitRequest;

/**
 * 答辩与成绩（阶段四：答辩安排 / 分项评分 / 总评合成发布）。
 */
public interface GraduationDefenseService {

    /** 院系安排/更新答辩（R-9.1，门禁 R-3.3：查重通过学生） */
    DefenseResponse arrangeDefense(Long deptUserId, DefenseArrangeRequest request);

    /** 答辩安排列表（教务全部/院系本院系/学生本人） */
    List<DefenseResponse> listDefenses(Long campaignId, String userType, Long userId);

    /** 指导教师录入指导分（R-9.2/R-9.3） */
    ScoreResponse submitAdvisorScore(Long teacherUserId, ScoreSubmitRequest request);

    /** 评阅教师录入评阅分（答辩安排的评阅教师） */
    ScoreResponse submitReviewerScore(Long reviewerUserId, ScoreSubmitRequest request);

    /** 院系/教务录入答辩分 */
    ScoreResponse submitDefenseScore(Long userId, String userType, ScoreSubmitRequest request);

    /** 院系确认并发布总评成绩（R-9.3） */
    ScoreResponse confirmScore(Long deptUserId, ScoreConfirmRequest request);

    /** 成绩列表（教务全部/院系本院系/学生本人/教师名下） */
    List<ScoreResponse> listScores(Long campaignId, String userType, Long userId);

    /** 学生查看本人成绩 */
    ScoreResponse getMyScore(Long studentUserId, Long campaignId);

    /** 教务导出成绩总表（R-9.4，复用导出能力，导出动作记日志） */
    ExportFile exportScores(Long academicUserId, Long campaignId);

    /**
     * 导出文件：字节 + 文件名。
     */
    record ExportFile(byte[] data, String fileName) {
    }
}
