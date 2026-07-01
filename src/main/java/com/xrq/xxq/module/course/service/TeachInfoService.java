package com.xrq.xxq.module.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xrq.xxq.module.course.dto.TeachInfoResponse;
import com.xrq.xxq.module.course.entity.TeachInfo;

import java.util.List;

public interface TeachInfoService extends IService<TeachInfo> {

    TeachInfoResponse getDetailById(Long id, Long userId, String userType);

    List<TeachInfoResponse> listByUserScope(Long userId, String userType, Long teacherId, Long courseId);
}
