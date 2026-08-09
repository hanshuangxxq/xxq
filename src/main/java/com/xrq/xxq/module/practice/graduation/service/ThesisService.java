package com.xrq.xxq.module.practice.graduation.service;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.Thesis;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;

/**
 * 毕业论文服务：学生提交（含文件）、教师/教务评审、下载。
 */
public interface ThesisService {

    ThesisResponse submit(Long studentUserId, ThesisSubmitRequest request, MultipartFile file);

    ThesisResponse review(Long thesisId, ThesisReviewRequest request, Long reviewerUserId, String userType);

    /** 学生查看自己的论文。 */
    ThesisResponse getMyThesis(Long studentUserId);

    /**
     * 处理人待办列表。
     * <ul>
     *   <li>教务：全部论文。</li>
     *   <li>教师：仅本人指导的论文。</li>
     * </ul>
     */
    PageResult<ThesisResponse> listForHandler(Long handlerUserId, String userType,
                                              ThesisStatusEnum status, PageQuery pageQuery);

    /** 取论文实体（含文件名），供下载接口鉴权后流式返回。 */
    Thesis loadForDownload(Long thesisId, Long operatorUserId, String userType);

    /**
     * 删除论文。
     * <ul>
     *   <li>教务：全权。</li>
     *   <li>教师：仅本人指导的论文。</li>
     *   <li>学生：仅本人且状态为 SUBMITTED（提交后未评审前可撤回）。</li>
     * </ul>
     */
    void deleteThesis(Long thesisId, Long operatorUserId, String userType);
}
