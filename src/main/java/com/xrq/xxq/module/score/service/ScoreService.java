package com.xrq.xxq.module.score.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.exam.dto.MakeupScoreEntryRequest;
import com.xrq.xxq.module.score.dto.ScoreBatchRequest;
import com.xrq.xxq.module.score.dto.ScoreEntryRequest;
import com.xrq.xxq.module.score.dto.ScoreRosterDto;
import com.xrq.xxq.module.score.dto.ScoreStatisticsDto;
import com.xrq.xxq.module.score.dto.ScoreView;
import com.xrq.xxq.module.score.entity.Score;

/**
 * 成绩服务：录入（录入即生效 + 不及格自动通知）、查询、名单。
 */
public interface ScoreService extends IService<Score> {

    /** 校验当前用户是否可录入/修改该授课安排的成绩（教师本人或教务）。 */
    void assertCanEnterTeachInfo(Long teachInfoId, Long userId, String userType);

    /**
     * 录入前取学生名单（常规班按班级 + 公选课班按选课成员）。
     * <p>examId 非空时按考试排考班级过滤合班名单，仅返回参加该考试的学生。
     */
    List<ScoreRosterDto> roster(Long teachInfoId, Long examId);

    /** 批量录入成绩（录入即生效；新建且不及格者自动发送站内消息）。 */
    List<ScoreView> saveScores(ScoreBatchRequest request, Long enterUserId, String userType);

    /** 修改单条成绩（未锁定）。 */
    ScoreView updateScore(Long scoreId, ScoreEntryRequest entry, Long enterUserId, String userType);

    /** 录入补考/重修考试成绩（生成 MAKEUP/RETAKE 成绩，关联原不及格记录）。 */
    List<ScoreView> enterMakeupScore(Long examId, List<MakeupScoreEntryRequest> entries, Long enterUserId, String userType);

    /** 按授课安排查询成绩（教师本人/院系本院/教务全部）。 */
    List<ScoreView> listByTeachInfo(Long teachInfoId, Long userId, String userType);

    /** 学生查询自己的成绩（可按学期过滤）。 */
    List<ScoreView> listMyScores(Long studentUserId, Long semesterId);

    /**
     * 成绩统计：按课程聚合分布（优/良/中/及格/不及格、平均/最高/最低/及格率）。
     * 院系仅本院学生、教务全校；可按课程/班级/学期过滤。
     */
    List<ScoreStatisticsDto> statistics(Long courseId, String className, Long semesterId, Long userId, String userType);
}
