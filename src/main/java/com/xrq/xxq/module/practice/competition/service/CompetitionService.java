package com.xrq.xxq.module.practice.competition.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.competition.dto.CompetitionCreateRequest;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResponse;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResultRequest;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResultResponse;
import com.xrq.xxq.module.practice.competition.dto.CompetitionUpdateRequest;
import com.xrq.xxq.module.practice.competition.dto.RegistrationRequest;
import com.xrq.xxq.module.practice.competition.dto.RegistrationResponse;
import com.xrq.xxq.module.practice.competition.dto.RegistrationReviewRequest;
import com.xrq.xxq.module.practice.competition.entity.CompetitionStatusEnum;

/**
 * 竞赛服务：发布/审核/录结果（教务）、报名（学生）。
 */
public interface CompetitionService {

    CompetitionResponse createCompetition(CompetitionCreateRequest request);

    CompetitionResponse updateCompetition(Long id, CompetitionUpdateRequest request);

    void changeCompetitionStatus(Long id, CompetitionStatusEnum status);

    PageResult<CompetitionResponse> listCompetitions(CompetitionStatusEnum status, PageQuery pageQuery);

    CompetitionResponse getCompetition(Long id);

    List<CompetitionResponse> listAvailableCompetitions(Long studentUserId);

    RegistrationResponse register(Long studentUserId, RegistrationRequest request);

    void cancelRegistration(Long studentUserId, Long registrationId);

    RegistrationResponse reviewRegistration(Long registrationId, RegistrationReviewRequest request);

    List<RegistrationResponse> listMyRegistrations(Long studentUserId);

    PageResult<RegistrationResponse> listRegistrationsByCompetition(Long competitionId, PageQuery pageQuery);

    void deleteCompetition(Long id);

    CompetitionResultResponse recordResult(CompetitionResultRequest request);

    List<CompetitionResultResponse> listResults(Long competitionId);

    CompetitionResultResponse getMyResult(Long studentUserId, Long competitionId);

    /** 删除竞赛结果（仅教务）。 */
    void deleteResult(Long resultId);

    /** 教务上传/替换获奖证书（file 与 filePath 二选一，必填其一） */
    CompetitionResultResponse uploadCertificate(Long academicUserId, Long resultId, String filePath,
                                                String fileOriginal, MultipartFile file);

    /** 获奖证书下载（教务/获奖学生本人；未上传 404） */
    FileView resolveCertificateFile(String userType, Long userId, Long resultId);
}
