package com.xrq.xxq.module.analysis.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.dto.ItemCreateRequest;
import com.xrq.xxq.module.analysis.dto.ItemResponse;
import com.xrq.xxq.module.analysis.dto.ItemUpdateRequest;
import com.xrq.xxq.module.analysis.entity.EvaluationItem;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplateItem;
import com.xrq.xxq.module.analysis.mapper.EvaluationItemMapper;
import com.xrq.xxq.module.analysis.mapper.EvaluationTemplateItemMapper;
import com.xrq.xxq.module.analysis.service.EvaluationItemService;

import lombok.RequiredArgsConstructor;

/**
 * 评教指标库服务实现。
 * <p>更新指标时 {@code updateTemplates=true} 同步刷新引用该指标的 evaluation_template_item 快照；
 * 否则模板保留旧快照。已提交的 teaching_evaluation_score 快照永不受影响。
 */
@Service
@RequiredArgsConstructor
public class EvaluationItemServiceImpl implements EvaluationItemService {

    private static final int DEFAULT_MAX_SCORE = 5;

    private final EvaluationItemMapper itemMapper;
    private final EvaluationTemplateItemMapper templateItemMapper;

    @Override
    @Transactional
    public ItemResponse createItem(ItemCreateRequest req, Long userId) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(400, "指标名称不能为空");
        }
        int maxScore = normalizeMaxScore(req.getMaxScore());
        if (itemMapper.selectCount(new LambdaQueryWrapper<EvaluationItem>()
                .eq(EvaluationItem::getName, req.getName())) > 0) {
            throw new BusinessException(409, "指标名称已存在");
        }
        EvaluationItem item = new EvaluationItem();
        item.setName(req.getName());
        item.setDescription(req.getDescription());
        item.setMaxScore(maxScore);
        item.setCreateUserId(userId);
        itemMapper.insert(item);
        return toResponse(item, 0);
    }

    @Override
    public List<ItemResponse> listItems() {
        List<EvaluationItem> items = itemMapper.selectList(new LambdaQueryWrapper<EvaluationItem>()
                .orderByDesc(EvaluationItem::getCreateTime));
        Map<Long, Long> used = loadUsedCount(items);
        return items.stream()
                .map(i -> toResponse(i, used.getOrDefault(i.getId(), 0L).intValue()))
                .toList();
    }

    @Override
    @Transactional
    public ItemResponse updateItem(Long id, ItemUpdateRequest req) {
        EvaluationItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "指标不存在");
        }
        boolean sync = Boolean.TRUE.equals(req.getUpdateTemplates());

        if (req.getName() != null) {
            if (req.getName().isBlank()) {
                throw new BusinessException(400, "指标名称不能为空");
            }
            if (!req.getName().equals(item.getName())
                    && itemMapper.selectCount(new LambdaQueryWrapper<EvaluationItem>()
                            .eq(EvaluationItem::getName, req.getName())
                            .ne(EvaluationItem::getId, id)) > 0) {
                throw new BusinessException(409, "指标名称已存在");
            }
            item.setName(req.getName());
        }
        if (req.getDescription() != null) {
            item.setDescription(req.getDescription());
        }
        if (req.getMaxScore() != null) {
            item.setMaxScore(normalizeMaxScore(req.getMaxScore()));
        }
        itemMapper.updateById(item);

        // 可选同步：刷新引用本指标的模板快照（item_name/max_score）
        if (sync) {
            EvaluationTemplateItem patch = new EvaluationTemplateItem();
            patch.setItemName(item.getName());
            patch.setMaxScore(item.getMaxScore());
            templateItemMapper.update(patch, new LambdaQueryWrapper<EvaluationTemplateItem>()
                    .eq(EvaluationTemplateItem::getItemId, id));
        }

        Long used = templateItemMapper.selectCount(new LambdaQueryWrapper<EvaluationTemplateItem>()
                .eq(EvaluationTemplateItem::getItemId, id));
        return toResponse(item, used == null ? 0 : used.intValue());
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        if (itemMapper.selectById(id) == null) {
            throw new BusinessException(404, "指标不存在");
        }
        Long used = templateItemMapper.selectCount(new LambdaQueryWrapper<EvaluationTemplateItem>()
                .eq(EvaluationTemplateItem::getItemId, id));
        if (used != null && used > 0) {
            throw new BusinessException(409, "指标已被模板引用，无法删除");
        }
        itemMapper.deleteById(id);
    }

    // ==================== 辅助 ====================

    private int normalizeMaxScore(Integer maxScore) {
        int v = maxScore == null ? DEFAULT_MAX_SCORE : maxScore;
        if (v < 1 || v > 100) {
            throw new BusinessException(400, "满分须在 1-100 之间");
        }
        return v;
    }

    private Map<Long, Long> loadUsedCount(List<EvaluationItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = items.stream().map(EvaluationItem::getId).toList();
        return templateItemMapper.selectList(new LambdaQueryWrapper<EvaluationTemplateItem>()
                        .in(EvaluationTemplateItem::getItemId, ids)).stream()
                .collect(Collectors.groupingBy(EvaluationTemplateItem::getItemId, Collectors.counting()));
    }

    private ItemResponse toResponse(EvaluationItem item, int usedCount) {
        ItemResponse r = new ItemResponse();
        r.setId(item.getId());
        r.setName(item.getName());
        r.setDescription(item.getDescription());
        r.setMaxScore(item.getMaxScore());
        r.setUsedCount(usedCount);
        r.setCreateTime(item.getCreateTime());
        return r;
    }
}
