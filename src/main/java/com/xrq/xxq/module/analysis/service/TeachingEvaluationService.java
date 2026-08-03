package com.xrq.xxq.module.analysis.service;

import java.util.List;

import com.xrq.xxq.module.analysis.dto.EvaluationStatusDto;
import com.xrq.xxq.module.analysis.dto.EvaluationSubmitRequest;
import com.xrq.xxq.module.analysis.dto.TeacherQualityDto;
import com.xrq.xxq.module.analysis.dto.TeachingEvaluationView;

/**
 * 教师教学质量评估服务：评教提交、自查、教师质量聚合（评教侧 + 成绩侧）。
 */
public interface TeachingEvaluationService {

    /** 学生提交评教（一人一授课安排一条，重复提交为更新）。 */
    TeachingEvaluationView submit(EvaluationSubmitRequest req, Long studentUserId);

    /** 学生查询本人已提交的评教。 */
    List<TeachingEvaluationView> myEvaluations(Long studentUserId);

    /** 单教师质量评估（教务/院系/教师本人）。 */
    TeacherQualityDto teacherQuality(Long teacherId, Long semesterId, Long callerUserId, String callerUserType);

    /** 教师查询本人教学质量。 */
    TeacherQualityDto myTeacherQuality(Long callerUserId, Long semesterId);

    /** 教务开启当前学期评教周期。 */
    EvaluationStatusDto openPeriod(Long callerUserId);

    /** 教务关闭当前学期评教周期。 */
    EvaluationStatusDto closePeriod(Long callerUserId);

    /** 查询当前学期评教周期状态；学生且已开放时附带可评课程列表（含课程名）。 */
    EvaluationStatusDto getPeriodStatus(Long callerUserId, String callerUserType);

    /** 教师质量列表/对比（教务全校、院系本院）。 */
    List<TeacherQualityDto> listTeacherQuality(Long semesterId, Long callerUserId, String callerUserType);
}
