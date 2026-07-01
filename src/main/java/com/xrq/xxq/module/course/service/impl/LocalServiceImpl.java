package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.Local;
import com.xrq.xxq.module.course.mapper.LocalMapper;
import com.xrq.xxq.module.course.service.LocalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalServiceImpl extends ServiceImpl<LocalMapper, Local> implements LocalService {
    private final LocalMapper localMapper;
}
