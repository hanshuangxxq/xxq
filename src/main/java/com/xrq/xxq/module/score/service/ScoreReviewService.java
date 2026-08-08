package com.xrq.xxq.module.score.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.score.dto.ReviewApplyRequest;
import com.xrq.xxq.module.score.dto.ReviewReplyRequest;
import com.xrq.xxq.module.score.dto.ReviewResolveRequest;
import com.xrq.xxq.module.score.dto.ReviewView;
import com.xrq.xxq.module.score.entity.ScoreReview;
import com.xrq.xxq.module.score.entity.ReviewStatusEnum;

/**
 * 成绩复核服务：学生申请 -> 教师回复（可调分）-> 学生升级 -> 教务终审（可调分+锁定）。
 */
public interface ScoreReviewService extends IService<ScoreReview> {

    /** 学生提交复核申请。 */
    ReviewView apply(ReviewApplyRequest request, Long studentUserId);

    /** 学生查询自己的复核申请。 */
    List<ReviewView> listMy(Long studentUserId);

    /** 处理人查询待办：教师看其课程相关，教务看全部；可按状态过滤。 */
    PageResult<ReviewView> listForHandler(Long userId, String userType, ReviewStatusEnum status, PageQuery pageQuery);

    /** 教师回复（可调分）。 */
    ReviewView teacherReply(Long reviewId, ReviewReplyRequest request, Long userId, String userType);

    /** 学生升级到教务。 */
    void escalate(Long reviewId, Long studentUserId);

    /** 教务终审（可调分并锁定成绩）。 */
    ReviewView adminResolve(Long reviewId, ReviewResolveRequest request, Long adminUserId);
}
