package com.xrq.xxq.module.analysis.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.dto.EvaluationTemplateView;
import com.xrq.xxq.module.analysis.dto.TemplateCreateRequest;
import com.xrq.xxq.module.analysis.dto.TemplateItemDto;
import com.xrq.xxq.module.analysis.dto.TemplateOverrideRequest;
import com.xrq.xxq.module.analysis.dto.TemplateResponse;
import com.xrq.xxq.module.analysis.dto.TemplateUpdateRequest;
import com.xrq.xxq.module.analysis.entity.EvaluationItem;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplate;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplateItem;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplateOverride;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplateStatusEnum;
import com.xrq.xxq.module.analysis.entity.TeachingEvaluation;
import com.xrq.xxq.module.analysis.mapper.EvaluationItemMapper;
import com.xrq.xxq.module.analysis.mapper.EvaluationTemplateItemMapper;
import com.xrq.xxq.module.analysis.mapper.EvaluationTemplateMapper;
import com.xrq.xxq.module.analysis.mapper.EvaluationTemplateOverrideMapper;
import com.xrq.xxq.module.analysis.mapper.TeachingEvaluationMapper;
import com.xrq.xxq.module.analysis.service.EvaluationTemplateService;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ReferenceValidator;

import lombok.RequiredArgsConstructor;

/**
 * 评教模板服务实现。
 * <p>模板内各指标 max_score 须一致（保证 avg_score 量纲统一）；
 * 模板-指标关联在绑定时快照指标名/满分；更新模板指标为整体替换。
 */
@Service
@RequiredArgsConstructor
public class EvaluationTemplateServiceImpl implements EvaluationTemplateService {

    private final EvaluationTemplateMapper templateMapper;
    private final EvaluationTemplateItemMapper templateItemMapper;
    private final EvaluationItemMapper itemMapper;
    private final EvaluationTemplateOverrideMapper overrideMapper;
    private final TeachingEvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final TeachInfoMapper teachInfoMapper;
    private final ReferenceValidator referenceValidator;

    // ==================== 模板 CRUD ====================

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest req, Long userId) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(400, "模板名称不能为空");
        }
        List<EvaluationItem> items = validateItems(req.getItems());

        EvaluationTemplate t = new EvaluationTemplate();
        t.setName(req.getName());
        t.setDescription(req.getDescription());
        t.setStatus(EvaluationTemplateStatusEnum.ENABLED);
        t.setIsDefault(0);
        referenceValidator.requireExists(userMapper, userId, "用户");
        t.setCreateUserId(userId);
        templateMapper.insert(t);
        bindItems(t.getId(), req.getItems(), items);
        return toResponse(t);
    }

    @Override
    public List<TemplateResponse> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<EvaluationTemplate>()
                        .orderByDesc(EvaluationTemplate::getIsDefault)
                        .orderByDesc(EvaluationTemplate::getCreateTime)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public TemplateResponse getTemplate(Long id) {
        EvaluationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        return toResponse(t);
    }

    @Override
    @Transactional
    public TemplateResponse updateTemplate(Long id, TemplateUpdateRequest req) {
        EvaluationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (req.getName() != null) {
            if (req.getName().isBlank()) {
                throw new BusinessException(400, "模板名称不能为空");
            }
            t.setName(req.getName());
        }
        if (req.getDescription() != null) {
            t.setDescription(req.getDescription());
        }
        templateMapper.updateById(t);

        // items 传入则增量更新模板指标（按 itemId diff，避免全删全插）
        if (req.getItems() != null) {
            List<EvaluationItem> items = validateItems(req.getItems());
            rebindTemplateItems(id, req.getItems(), items);
        }
        return toResponse(templateMapper.selectById(id));
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        EvaluationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (Integer.valueOf(1).equals(t.getIsDefault())) {
            throw new BusinessException(409, "默认模板不可删除");
        }
        if (overrideMapper.selectCount(new LambdaQueryWrapper<EvaluationTemplateOverride>()
                .eq(EvaluationTemplateOverride::getTemplateId, id)) > 0) {
            throw new BusinessException(409, "模板被课程覆盖引用，无法删除");
        }
        if (evaluationMapper.selectCount(new LambdaQueryWrapper<TeachingEvaluation>()
                .eq(TeachingEvaluation::getTemplateId, id)) > 0) {
            throw new BusinessException(409, "模板已有评教记录，无法删除");
        }
        templateItemMapper.delete(new LambdaQueryWrapper<EvaluationTemplateItem>()
                .eq(EvaluationTemplateItem::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        EvaluationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (t.getStatus() != EvaluationTemplateStatusEnum.ENABLED) {
            throw new BusinessException(409, "停用模板不可设为默认");
        }
        if (templateMapper.selectCount(new LambdaQueryWrapper<EvaluationTemplate>()
                .eq(EvaluationTemplate::getIsDefault, 1)
                .ne(EvaluationTemplate::getId, id)) > 0) {
            throw new BusinessException(409, "已有默认模板");
        }
        // 原默认置普通
        EvaluationTemplate unset = new EvaluationTemplate();
        unset.setIsDefault(0);
        templateMapper.update(unset, new LambdaQueryWrapper<EvaluationTemplate>()
                .eq(EvaluationTemplate::getIsDefault, 1)
                .ne(EvaluationTemplate::getId, id));
        t.setIsDefault(1);
        templateMapper.updateById(t);
    }

    @Override
    public void updateStatus(Long id, EvaluationTemplateStatusEnum status) {
        EvaluationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (status == EvaluationTemplateStatusEnum.DISABLED
                && Integer.valueOf(1).equals(t.getIsDefault())) {
            throw new BusinessException(409, "默认模板不可停用");
        }
        t.setStatus(status);
        templateMapper.updateById(t);
    }

    // ==================== 课程覆盖 ====================

    @Override
    @Transactional
    public void setOverride(Long teachInfoId, TemplateOverrideRequest req) {
        referenceValidator.requireExists(teachInfoMapper, teachInfoId, "授课安排");
        Long templateId = req == null ? null : req.getTemplateId();
        if (templateId == null) {
            // 清除覆盖，回退到全局默认模板
            overrideMapper.delete(new LambdaQueryWrapper<EvaluationTemplateOverride>()
                    .eq(EvaluationTemplateOverride::getTeachInfoId, teachInfoId));
            return;
        }
        EvaluationTemplate t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (t.getStatus() != EvaluationTemplateStatusEnum.ENABLED) {
            throw new BusinessException(409, "停用模板不可设为覆盖");
        }
        EvaluationTemplateOverride exist = overrideMapper.selectOne(
                new LambdaQueryWrapper<EvaluationTemplateOverride>()
                        .eq(EvaluationTemplateOverride::getTeachInfoId, teachInfoId));
        if (exist == null) {
            EvaluationTemplateOverride o = new EvaluationTemplateOverride();
            o.setTeachInfoId(teachInfoId);
            o.setTemplateId(templateId);
            overrideMapper.insert(o);
        } else {
            exist.setTemplateId(templateId);
            overrideMapper.updateById(exist);
        }
    }

    @Override
    public TemplateResponse getOverride(Long teachInfoId) {
        EvaluationTemplateOverride o = overrideMapper.selectOne(
                new LambdaQueryWrapper<EvaluationTemplateOverride>()
                        .eq(EvaluationTemplateOverride::getTeachInfoId, teachInfoId));
        if (o == null) {
            return null;
        }
        EvaluationTemplate t = templateMapper.selectById(o.getTemplateId());
        return t != null ? toResponse(t) : null;
    }

    // ==================== 评教表单 ====================

    @Override
    public EvaluationTemplateView getEvaluationForm(Long teachInfoId) {
        EvaluationTemplate t = resolveTemplate(teachInfoId);
        if (t == null) {
            throw new BusinessException(400, "暂未配置评教模板，请联系教务");
        }
        if (t.getStatus() != EvaluationTemplateStatusEnum.ENABLED) {
            throw new BusinessException(400, "当前评教模板不可用");
        }
        EvaluationTemplateView v = new EvaluationTemplateView();
        v.setTemplateId(t.getId());
        v.setTemplateName(t.getName());
        v.setItems(loadItemDtos(t.getId()));
        return v;
    }

    /** 解析授课安排所用模板：课程覆盖优先，否则全局默认。 */
    private EvaluationTemplate resolveTemplate(Long teachInfoId) {
        EvaluationTemplateOverride o = overrideMapper.selectOne(
                new LambdaQueryWrapper<EvaluationTemplateOverride>()
                        .eq(EvaluationTemplateOverride::getTeachInfoId, teachInfoId));
        if (o != null) {
            return templateMapper.selectById(o.getTemplateId());
        }
        return templateMapper.selectOne(new LambdaQueryWrapper<EvaluationTemplate>()
                .eq(EvaluationTemplate::getIsDefault, 1));
    }

    // ==================== 指标校验与绑定 ====================

    /** 校验指标列表：非空、无重复、均存在、满分一致。返回库中指标实体列表。 */
    private List<EvaluationItem> validateItems(List<TemplateItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "至少需要 1 个指标");
        }
        List<Long> ids = items.stream().map(TemplateItemDto::getItemId).toList();
        Set<Long> distinct = new HashSet<>(ids);
        if (distinct.size() != ids.size()) {
            throw new BusinessException(400, "指标不可重复");
        }
        List<EvaluationItem> entities = itemMapper.selectByIds(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(400, "存在无效指标");
        }
        Set<Integer> maxes = entities.stream().map(EvaluationItem::getMaxScore).collect(Collectors.toSet());
        if (maxes.size() > 1) {
            throw new BusinessException(400, "模板内各指标满分须一致");
        }
        return entities;
    }

    private void bindItems(Long templateId, List<TemplateItemDto> items, List<EvaluationItem> entities) {
        referenceValidator.requireExists(templateMapper, templateId, "评教模板");
        Map<Long, EvaluationItem> map = entities.stream()
                .collect(Collectors.toMap(EvaluationItem::getId, i -> i));
        int order = 1;
        for (TemplateItemDto dto : items) {
            EvaluationItem it = map.get(dto.getItemId());
            if (templateItemMapper.selectCount(new LambdaQueryWrapper<EvaluationTemplateItem>()
                    .eq(EvaluationTemplateItem::getTemplateId, templateId)
                    .eq(EvaluationTemplateItem::getItemId, it.getId())) > 0) {
                throw new BusinessException(409, "模板指标已存在");
            }
            EvaluationTemplateItem rel = new EvaluationTemplateItem();
            rel.setTemplateId(templateId);
            rel.setItemId(it.getId());
            rel.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order);
            rel.setRequired(dto.getRequired() == null ? 1 : dto.getRequired());
            templateItemMapper.insert(rel);
            order++;
        }
    }

    /**
     * 增量更新模板指标：按 itemId diff，删除移除项、新增新项、更新保留项的 sortOrder/required。
     */
    private void rebindTemplateItems(Long templateId, List<TemplateItemDto> items, List<EvaluationItem> entities) {
        List<EvaluationTemplateItem> existing = templateItemMapper.selectList(
                new LambdaQueryWrapper<EvaluationTemplateItem>()
                        .eq(EvaluationTemplateItem::getTemplateId, templateId));
        Map<Long, EvaluationTemplateItem> existingByItemId = existing.stream()
                .collect(Collectors.toMap(EvaluationTemplateItem::getItemId, t -> t, (a, b) -> a));
        Set<Long> newItemIds = items.stream().map(TemplateItemDto::getItemId).collect(Collectors.toSet());

        // 删除：现有 - 新
        for (EvaluationTemplateItem rel : existing) {
            if (!newItemIds.contains(rel.getItemId())) {
                templateItemMapper.deleteById(rel.getId());
            }
        }
        // 新增/更新
        int order = 1;
        for (TemplateItemDto dto : items) {
            EvaluationTemplateItem existingRel = existingByItemId.get(dto.getItemId());
            if (existingRel == null) {
                EvaluationTemplateItem rel = new EvaluationTemplateItem();
                rel.setTemplateId(templateId);
                rel.setItemId(dto.getItemId());
                rel.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order);
                rel.setRequired(dto.getRequired() == null ? 1 : dto.getRequired());
                templateItemMapper.insert(rel);
            } else {
                existingRel.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order);
                existingRel.setRequired(dto.getRequired() == null ? 1 : dto.getRequired());
                templateItemMapper.updateById(existingRel);
            }
            order++;
        }
    }

    // ==================== 转换 ====================

    private TemplateResponse toResponse(EvaluationTemplate t) {
        TemplateResponse r = new TemplateResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setStatus(t.getStatus());
        r.setIsDefault(t.getIsDefault());
        r.setItems(loadItemDtos(t.getId()));
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        return r;
    }

    private List<TemplateItemDto> loadItemDtos(Long templateId) {
        List<EvaluationTemplateItem> rels = templateItemMapper.selectList(
                        new LambdaQueryWrapper<EvaluationTemplateItem>()
                                .eq(EvaluationTemplateItem::getTemplateId, templateId)
                                .orderByAsc(EvaluationTemplateItem::getSortOrder));
        if (rels.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = rels.stream().map(EvaluationTemplateItem::getItemId).distinct().toList();
        Map<Long, EvaluationItem> itemMap = itemMapper.selectByIds(itemIds).stream()
                .collect(Collectors.toMap(EvaluationItem::getId, i -> i));
        return rels.stream().map(rel -> {
                    TemplateItemDto d = new TemplateItemDto(rel.getItemId());
                    EvaluationItem item = itemMap.get(rel.getItemId());
                    if (item != null) {
                        d.setItemName(item.getName());
                        d.setMaxScore(item.getMaxScore());
                    }
                    d.setSortOrder(rel.getSortOrder());
                    d.setRequired(rel.getRequired());
                    return d;
                })
                .toList();
    }
}
