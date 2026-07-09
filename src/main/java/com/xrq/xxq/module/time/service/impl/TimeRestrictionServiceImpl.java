package com.xrq.xxq.module.time.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.time.entity.TimeRestriction;
import com.xrq.xxq.module.time.mapper.TimeRestrictionMapper;
import com.xrq.xxq.module.time.service.TimeRestrictionService;
import org.springframework.stereotype.Service;

@Service
public class TimeRestrictionServiceImpl extends ServiceImpl<TimeRestrictionMapper, TimeRestriction>
        implements TimeRestrictionService {
}
