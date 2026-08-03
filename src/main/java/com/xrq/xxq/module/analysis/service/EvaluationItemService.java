package com.xrq.xxq.module.analysis.service;

import java.util.List;

import com.xrq.xxq.module.analysis.dto.ItemCreateRequest;
import com.xrq.xxq.module.analysis.dto.ItemResponse;
import com.xrq.xxq.module.analysis.dto.ItemUpdateRequest;

/**
 * 评教指标库服务：指标 CRUD；更新时可选同步快照到引用该指标的模板。
 */
public interface EvaluationItemService {

    ItemResponse createItem(ItemCreateRequest req, Long userId);

    List<ItemResponse> listItems();

    ItemResponse updateItem(Long id, ItemUpdateRequest req);

    void deleteItem(Long id);
}
