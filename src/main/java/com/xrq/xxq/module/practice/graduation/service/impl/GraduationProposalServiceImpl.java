package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.graduation.dto.ProposalDeclareRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalResponse;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewView;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.entity.GraduationProposal;
import com.xrq.xxq.module.practice.graduation.entity.GraduationProposalReview;
import com.xrq.xxq.module.practice.graduation.entity.ProposalReviewStageEnum;
import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationProposalMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationProposalReviewMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.practice.graduation.service.GraduationProposalService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.DistributedLock;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationProposalServiceImpl
        extends ServiceImpl<GraduationProposalMapper, GraduationProposal>
        implements GraduationProposalService {

    private final GraduationCampaignMapper campaignMapper;
    private final GraduationProposalReviewMapper reviewMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final StudentScopeResolver scopeResolver;
    private final DistributedLock distributedLock;
    private final GraduationLogService logService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ProposalResponse declareProposal(Long studentUserId, ProposalDeclareRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonBlank(request.getTitle(), "题目名称");
        ParamValidator.requireNonBlank(request.getContent(), "主要内容说明");
        if (request.getContent().trim().length() < 100) {
            throw new BusinessException(400, "主要内容说明不少于100字");
        }
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放选题");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getTopicStartTime())) {
            throw new BusinessException(409, "选题尚未开始");
        }
        if (now.isAfter(campaign.getTopicEndTime())) {
            throw new BusinessException(409, "选题已截止");
        }
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, studentUserId)
                .last("LIMIT 1"));
        if (student == null) {
            throw new BusinessException(404, "学生档案不存在");
        }
        if (!gradeAllowed(campaign, student.getGradeId())) {
            throw new BusinessException(403, "当前年级不参与本活动");
        }

        // R-5.3：同一时间仅一条有效申请；锁保护提交/重提的并发窗口
        return distributedLock.withLock("grad:proposal:" + campaign.getId() + ":" + studentUserId, 30, () -> {
            GraduationProposal existing = baseMapper.selectOne(new LambdaQueryWrapper<GraduationProposal>()
                    .eq(GraduationProposal::getCampaignId, campaign.getId())
                    .eq(GraduationProposal::getStudentId, studentUserId)
                    .last("LIMIT 1"));
            if (existing != null && existing.getStatus() != ProposalStatusEnum.REJECTED) {
                throw new BusinessException(409, "你已存在进行中的选题申请，驳回后才能修改重提");
            }
            GraduationProposal proposal;
            if (existing == null) {
                proposal = new GraduationProposal();
                proposal.setCampaignId(campaign.getId());
                proposal.setStudentId(studentUserId);
                proposal.setStatus(ProposalStatusEnum.PENDING_DEPT);
            } else {
                // 驳回后重提：重新走完整审批流程（R-5.3）
                proposal = existing;
                proposal.setStatus(ProposalStatusEnum.PENDING_DEPT);
                proposal.setRejectReason(null);
            }
            proposal.setTitle(request.getTitle().trim());
            proposal.setContent(request.getContent().trim());
            proposal.setSubmitTime(now);
            if (existing == null) {
                baseMapper.insert(proposal);
            } else {
                baseMapper.updateById(proposal);
            }
            return toResponse(proposal, studentName(studentUserId), studentNo(studentUserId));
        });
    }

    @Override
    @Transactional
    public ProposalResponse reviewProposal(Long reviewerUserId, String reviewerType,
                                           Long proposalId, ProposalReviewStageEnum stage,
                                           ProposalReviewRequest request) {
        if (stage == null) {
            throw new BusinessException(400, "审批级别不能为空");
        }
        if (request.getApprove() == null) {
            throw new BusinessException(400, "审批结果不能为空");
        }
        GraduationProposal proposal = baseMapper.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(404, "选题申请不存在");
        }
        GraduationCampaign campaign = campaignMapper.selectById(proposal.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        boolean approve = request.getApprove();
        if (!approve && (request.getComment() == null || request.getComment().isBlank())) {
            throw new BusinessException(400, "驳回必须填写理由");
        }

        if (stage == ProposalReviewStageEnum.DEPT) {
            if (proposal.getStatus() != ProposalStatusEnum.PENDING_DEPT) {
                throw new BusinessException(409, "该申请不在待院系初审状态");
            }
            // R-5.5：院系初审仅限本学院学生
            if (scopeResolver.departmentOwnsStudent(reviewerUserId, proposal.getStudentId())) {
                throw new BusinessException(403, "权限不足");
            }
        } else {
            if (proposal.getStatus() != ProposalStatusEnum.DEPT_APPROVED) {
                throw new BusinessException(409, "该申请不在待教务终审状态");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        proposal.setStatus(approve
                ? (stage == ProposalReviewStageEnum.DEPT ? ProposalStatusEnum.DEPT_APPROVED : ProposalStatusEnum.APPROVED)
                : ProposalStatusEnum.REJECTED);
        proposal.setRejectReason(approve ? null : request.getComment());
        baseMapper.updateById(proposal);

        // 审批流水留痕（R-5.7）
        GraduationProposalReview review = new GraduationProposalReview();
        review.setProposalId(proposal.getId());
        review.setStage(stage);
        review.setAction(approve ? "APPROVE" : "REJECT");
        review.setReviewerId(reviewerUserId);
        review.setReviewTime(now);
        review.setComment(request.getComment());
        reviewMapper.insert(review);

        logService.record(proposal.getCampaignId(), reviewerUserId, reviewerType,
                stage == ProposalReviewStageEnum.DEPT ? "院系初审" : "教务终审",
                "graduation_proposal", proposal.getId(),
                "结果: " + (approve ? "通过" : "驳回") + ", 题目: " + proposal.getTitle());

        String content = "您的选题《" + proposal.getTitle() + "》"
                + (stage == ProposalReviewStageEnum.DEPT ? "院系初审" : "教务终审")
                + (approve ? "已通过。" : "被驳回：" + request.getComment());
        eventPublisher.publishEvent(new PracticeNoticeEvent(proposal.getStudentId(),
                "毕业选题审批结果", content));
        return toResponse(proposal, studentName(proposal.getStudentId()), studentNo(proposal.getStudentId()));
    }

    @Override
    public List<ProposalResponse> listMyProposals(Long studentUserId) {
        List<GraduationProposal> list = baseMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getStudentId, studentUserId)
                .orderByDesc(GraduationProposal::getId));
        return toResponses(list, studentUserId);
    }

    @Override
    public List<ProposalResponse> listPendingDept(Long deptUserId, Long campaignId) {
        ParamValidator.requireNonNull(campaignId, "活动");
        List<GraduationProposal> all = baseMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, campaignId)
                .eq(GraduationProposal::getStatus, ProposalStatusEnum.PENDING_DEPT)
                .orderByAsc(GraduationProposal::getSubmitTime));
        // 仅本学院学生（R-10.1 数据可见性）：批量解析学生院系后内存过滤（替代逐条 departmentOwnsStudent 查库）
        Long deptCollegeId = scopeResolver.deptCollegeId(deptUserId);
        Map<Long, Long> collegeByStudent = scopeResolver.studentCollegeIdMap(
                all.stream().map(GraduationProposal::getStudentId).toList());
        List<GraduationProposal> scoped = all.stream()
                .filter(p -> deptCollegeId != null
                        && Objects.equals(deptCollegeId, collegeByStudent.get(p.getStudentId())))
                .toList();
        return toResponses(scoped, null);
    }

    @Override
    public List<ProposalResponse> listPendingAcademic(Long campaignId) {
        ParamValidator.requireNonNull(campaignId, "活动");
        List<GraduationProposal> list = baseMapper.selectList(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, campaignId)
                .eq(GraduationProposal::getStatus, ProposalStatusEnum.DEPT_APPROVED)
                .orderByAsc(GraduationProposal::getSubmitTime));
        return toResponses(list, null);
    }

    // ---- helpers ----

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private boolean gradeAllowed(GraduationCampaign campaign, Long gradeId) {
        if (gradeId == null || campaign.getAllowedGradeIds() == null) {
            return false;
        }
        for (String part : campaign.getAllowedGradeIds().split(",")) {
            if (String.valueOf(gradeId).equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<ProposalResponse> toResponses(List<GraduationProposal> proposals, Long singleStudentId) {
        if (proposals.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = proposals.stream().map(GraduationProposal::getStudentId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(studentIds);
        Map<Long, String> noMap = studentMapper.toStudentNoMap(studentIds);
        return proposals.stream()
                .map(p -> toResponse(p, nameMap.get(p.getStudentId()), noMap.get(p.getStudentId())))
                .toList();
    }

    private ProposalResponse toResponse(GraduationProposal proposal, String studentName, String studentNo) {
        ProposalResponse resp = new ProposalResponse();
        resp.setId(proposal.getId());
        resp.setCampaignId(proposal.getCampaignId());
        resp.setStudentId(proposal.getStudentId());
        resp.setStudentName(studentName);
        resp.setStudentNo(studentNo);
        resp.setTitle(proposal.getTitle());
        resp.setContent(proposal.getContent());
        resp.setStatus(proposal.getStatus());
        resp.setRejectReason(proposal.getRejectReason());
        resp.setSubmitTime(proposal.getSubmitTime());
        resp.setReviews(toReviewViews(proposal.getId()));
        return resp;
    }

    private List<ProposalReviewView> toReviewViews(Long proposalId) {
        List<GraduationProposalReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<GraduationProposalReview>()
                        .eq(GraduationProposalReview::getProposalId, proposalId)
                        .orderByAsc(GraduationProposalReview::getReviewTime));
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> reviewerIds = reviews.stream().map(GraduationProposalReview::getReviewerId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(reviewerIds);
        List<ProposalReviewView> views = new ArrayList<>();
        for (GraduationProposalReview r : reviews) {
            ProposalReviewView view = new ProposalReviewView();
            view.setStage(r.getStage());
            view.setAction(r.getAction());
            view.setReviewerId(r.getReviewerId());
            view.setReviewerName(nameMap.get(r.getReviewerId()));
            view.setReviewTime(r.getReviewTime());
            view.setComment(r.getComment());
            views.add(view);
        }
        return views;
    }

    private String studentName(Long studentUserId) {
        if (studentUserId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(studentUserId)).get(studentUserId);
    }

    private String studentNo(Long studentUserId) {
        if (studentUserId == null) {
            return null;
        }
        return studentMapper.toStudentNoMap(List.of(studentUserId)).get(studentUserId);
    }
}
