package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionClassResponse;

public interface SelectionClassService {

    /** 分班：按选课顺序 + 容量切分选课班。 */
    void finalize(Long campaignId);

    /** 查询分班结果。 */
    List<SelectionClassResponse> listByCampaign(Long campaignId);
}
