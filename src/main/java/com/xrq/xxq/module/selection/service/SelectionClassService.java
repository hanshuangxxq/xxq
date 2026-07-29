package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionClassResponse;

public interface SelectionClassService {

    /** 分班：按选课顺序 + 容量切分选课班。 */
    void finalize(Long campaignId);

    /** 查询分班结果。 */
    List<SelectionClassResponse> listByCampaign(Long campaignId);

    /**
     * 为指定选课班分配（或取消分配）任课教师。
     * teacherId 为 null 表示取消已分配的教师。
     */
    SelectionClassResponse assignTeacher(Long campaignId, Long classId, Long teacherId);
}
