package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.Time;
import com.xrq.xxq.module.course.mapper.TimeMapper;
import com.xrq.xxq.module.course.service.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimeServiceImpl extends ServiceImpl<TimeMapper, Time> implements TimeService {
    private final TimeMapper timeMapper;
}
