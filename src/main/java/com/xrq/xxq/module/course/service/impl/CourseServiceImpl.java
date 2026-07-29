package com.xrq.xxq.module.course.service.impl;

import java.io.Serializable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseService;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;

import lombok.RequiredArgsConstructor;

/**
 * 课程服务实现，继承 MyBatis Plus ServiceImpl 提供通用 CRUD。
 *
 * @类名 CourseServiceImpl
 * @Date 2026/6/30
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
    private final CourseMapper courseMapper;
    private final SelectionCampaignService selectionCampaignService;

    @Override
    @Transactional
    public boolean removeById(Serializable id) {
        selectionCampaignService.deleteByCourseId((Long) id);
        return super.removeById(id);
    }
}
