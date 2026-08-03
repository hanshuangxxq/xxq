package com.xrq.xxq.module.analysis.service;

import java.util.List;

import com.xrq.xxq.module.analysis.dto.EvaluationTemplateView;
import com.xrq.xxq.module.analysis.dto.TemplateCreateRequest;
import com.xrq.xxq.module.analysis.dto.TemplateOverrideRequest;
import com.xrq.xxq.module.analysis.dto.TemplateResponse;
import com.xrq.xxq.module.analysis.dto.TemplateUpdateRequest;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplateStatusEnum;

/**
 * 评教模板服务：模板 CRUD、设默认、启停、课程级覆盖、评教表单解析。
 */
public interface EvaluationTemplateService {

    // ---- 模板 ----
    TemplateResponse createTemplate(TemplateCreateRequest req, Long userId);

    List<TemplateResponse> listTemplates();

    TemplateResponse getTemplate(Long id);

    TemplateResponse updateTemplate(Long id, TemplateUpdateRequest req);

    void deleteTemplate(Long id);

    /** 设为全局默认模板（互斥：原默认置普通）。 */
    void setDefault(Long id);

    /** 启用/停用模板（默认模板不可停用）。 */
    void updateStatus(Long id, EvaluationTemplateStatusEnum status);

    // ---- 课程覆盖 ----
    void setOverride(Long teachInfoId, TemplateOverrideRequest req);

    TemplateResponse getOverride(Long teachInfoId);

    // ---- 评教表单 ----
    /**
     * 解析某授课安排所用评教模板（课程覆盖优先，否则全局默认），返回表单视图。
     * 无可用模板时抛业务异常。供学生表单接口与评教提交校验共用。
     */
    EvaluationTemplateView getEvaluationForm(Long teachInfoId);
}
