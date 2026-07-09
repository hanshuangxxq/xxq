package com.xrq.xxq.module.time.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.time.entity.Time;
import com.xrq.xxq.module.time.mapper.TimeMapper;
import com.xrq.xxq.module.time.service.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimeServiceImpl extends ServiceImpl<TimeMapper, Time> implements TimeService {
    private final TimeMapper timeMapper;
}
