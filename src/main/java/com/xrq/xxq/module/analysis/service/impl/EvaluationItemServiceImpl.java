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
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.ReferenceValidator;

import lombok.RequiredArgsConstructor;

/**
 * 评教指标库服务实现。
 * <p>指标名/满分仅存于 evaluation_item 表，模板通过 item_id 关联实时读取，无快照同步。
 * 已提交的 teaching_evaluation_score 快照永不受影响。
 */
@Service
@RequiredArgsConstructor
public class EvaluationItemServiceImpl implements EvaluationItemService {

    private static final int DEFAULT_MAX_SCORE = 5;

    private final EvaluationItemMapper itemMapper;
    private final EvaluationTemplateItemMapper templateItemMapper;
    private final UserMapper userMapper;
    private final ReferenceValidator referenceValidator;

    @Override
    @Transactional
    public ItemResponse createItem(ItemCreateRequest req, Long userId) {
        ParamValidator.requireNonBlank(req.getName(), "指标名称");
        int maxScore = normalizeMaxScore(req.getMaxScore());
        if (itemMapper.selectCount(new LambdaQueryWrapper<EvaluationItem>()
                .eq(EvaluationItem::getName, req.getName())) > 0) {
            throw new BusinessException(409, "指标名称已存在");
        }
        EvaluationItem item = new EvaluationItem();
        item.setName(req.getName());
        item.setDescription(req.getDescription());
        item.setMaxScore(maxScore);
        referenceValidator.requireExists(userMapper, userId, "用户");
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
        if (req.getName() != null) {
            ParamValidator.requireNonBlank(req.getName(), "指标名称");
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
