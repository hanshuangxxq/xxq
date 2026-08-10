package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.AllocationRequest;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentResponse;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignResponse;
import com.xrq.xxq.module.practice.graduation.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.practice.graduation.dto.GraduationExportRow;
import com.xrq.xxq.module.practice.graduation.dto.PickRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalDeclareRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalResponse;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewRequest;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;

/**
 * 毕业选题活动服务。
 * <p>
 * 流程：教务开启活动 -> 学生自拟选题 -> 院系初审(本学院) -> 教师自选/院系分配 -> 教务最终审查+导出。
 */
public interface GraduationService extends IService<GraduationCampaign> {

    // ---- 教务 ----
    CampaignResponse createCampaign(CampaignCreateRequest request);

    CampaignResponse updateCampaign(Long id, CampaignUpdateRequest request);

    /** 开放/关闭活动。 */
    void changeCampaignStatus(Long id, CampaignStatusEnum status);

    PageResult<CampaignResponse> listCampaigns(PageQuery pageQuery);

    CampaignResponse getCampaign(Long id);

    /** 教务最终审查匹配记录（通过/驳回；驳回则学生回匹配池）。 */
    AssignmentResponse reviewAssignment(Long assignmentId, AssignmentReviewRequest request);

    /** 导出活动全部申报（含匹配信息）供送查重。 */
    List<GraduationExportRow> exportAssignments(Long campaignId);

    // ---- 学生 ----
    ProposalResponse declareProposal(Long studentUserId, ProposalDeclareRequest request);

    void cancelProposal(Long studentUserId, Long proposalId);

    List<ProposalResponse> listMyProposals(Long studentUserId);

    /** 学生查看自己在某活动的匹配结果（无匹配返回 null）。 */
    AssignmentResponse getMyAssignment(Long studentUserId, Long campaignId);

    // ---- 院系管理者 ----
    ProposalResponse reviewProposal(Long deptUserId, Long proposalId, ProposalReviewRequest request);

    AssignmentResponse allocateStudent(Long deptUserId, AllocationRequest request);

    /** 本学院匹配池（DEPT_APPROVED 未匹配）。 */
    List<ProposalResponse> listDeptPool(Long deptUserId, Long campaignId);

    /** 本学院匹配记录。 */
    List<AssignmentResponse> listDeptAssignments(Long deptUserId, Long campaignId);

    // ---- 教师 ----
    AssignmentResponse pickStudent(Long teacherUserId, PickRequest request);

    void cancelPick(Long teacherUserId, Long assignmentId);

    List<AssignmentResponse> listTeacherAssignments(Long teacherUserId, Long campaignId);

    /** 教师可自选的本学院匹配池。 */
    List<ProposalResponse> listPickableProposals(Long teacherUserId, Long campaignId);
}
