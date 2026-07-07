package com.xrq.xxq.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.user.entity.Major;
import com.xrq.xxq.module.user.mapper.MajorMapper;
import com.xrq.xxq.module.user.service.MajorService;
import org.springframework.stereotype.Service;

@Service
public class MajorServiceImpl extends ServiceImpl<MajorMapper, Major> implements MajorService {
}
