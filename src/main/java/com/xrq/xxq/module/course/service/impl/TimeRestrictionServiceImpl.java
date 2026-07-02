package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.TimeRestriction;
import com.xrq.xxq.module.course.mapper.TimeRestrictionMapper;
import com.xrq.xxq.module.course.service.TimeRestrictionService;
import org.springframework.stereotype.Service;

@Service
public class TimeRestrictionServiceImpl extends ServiceImpl<TimeRestrictionMapper, TimeRestriction>
        implements TimeRestrictionService {
}
