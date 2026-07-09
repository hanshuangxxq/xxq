package com.xrq.xxq.module.mojor.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.mojor.service.MajorService;
import org.springframework.stereotype.Service;

@Service
public class MajorServiceImpl extends ServiceImpl<MajorMapper, Major> implements MajorService {
}
