package com.xrq.xxq.module.local.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.local.entity.Local;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.local.service.LocalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalServiceImpl extends ServiceImpl<LocalMapper, Local> implements LocalService {
    private final LocalMapper localMapper;
}
