package com.xrq.xxq.module.time.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.time.entity.TimeRestriction;
import com.xrq.xxq.module.time.mapper.TimeMapper;
import com.xrq.xxq.module.time.mapper.TimeRestrictionMapper;
import com.xrq.xxq.module.time.service.TimeRestrictionService;
import com.xrq.xxq.util.ReferenceValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimeRestrictionServiceImpl extends ServiceImpl<TimeRestrictionMapper, TimeRestriction>
        implements TimeRestrictionService {

    private final ReferenceValidator referenceValidator;
    private final TimeMapper timeMapper;

    @Override
    public boolean save(TimeRestriction entity) {
        validateReferences(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(TimeRestriction entity) {
        validateReferences(entity);
        return super.updateById(entity);
    }

    private void validateReferences(TimeRestriction entity) {
        referenceValidator.requireExists(timeMapper, entity.getTimeId(), "时间");
        // RESERVED 类型时校验课程引用（courseId 或 campaignId 非空）
        if (entity.getCourseId() != null || entity.getCampaignId() != null) {
            referenceValidator.requireCourseRef(entity.getCourseId(), entity.getCampaignId());
        }
    }
}
