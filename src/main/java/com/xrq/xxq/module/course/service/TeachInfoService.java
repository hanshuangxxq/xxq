package com.xrq.xxq.module.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.entity.TeachInfo;

import java.util.List;

public interface TeachInfoService extends IService<TeachInfo> {

    CourseDto getDetailById(Long id, Long userId, String userType);

    List<CourseDto> listByUserScope(Long userId, String userType, Long teacherId, Long courseId);

    List<ClassCourseDto> listClassCourses(Long userId);
}
