package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.xrq.xxq.module.practice.graduation.dto.ProposalDeclareRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalResponse;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewRequest;
import com.xrq.xxq.module.practice.graduation.entity.ProposalReviewStageEnum;

/**
 * 选题申请与两级审批（§5：学生自拟 + 院系初审 + 教务终审）。
 */
public interface GraduationProposalService {

    /** 学生提交/重提选题申请（R-5.1~R-5.4） */
    ProposalResponse declareProposal(Long studentUserId, ProposalDeclareRequest request);

    /** 两级审批（stage=DEPT 院系初审 / ACADEMIC 教务终审，R-5.5/R-5.6） */
    ProposalResponse reviewProposal(Long reviewerUserId, String reviewerType,
                                    Long proposalId, ProposalReviewStageEnum stage, ProposalReviewRequest request);

    /** 学生查看自己的申请列表 */
    List<ProposalResponse> listMyProposals(Long studentUserId);

    /** 院系待初审队列（本院系 PENDING_DEPT） */
    List<ProposalResponse> listPendingDept(Long deptUserId, Long campaignId);

    /** 教务待终审队列（DEPT_APPROVED） */
    List<ProposalResponse> listPendingAcademic(Long campaignId);
}
