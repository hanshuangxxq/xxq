package com.xrq.xxq.module.teachinfo.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.dto.UserCourseDto;
import com.xrq.xxq.module.course.dto.WeekScheduleDto;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;

import java.util.List;

public interface TeachInfoService extends IService<TeachInfo> {

    CourseDto getDetailById(Long id, Long userId, String userType);

    UserCourseDto listByUserScope(Long userId, String userType, Long teacherId, Long courseId, Integer week);

    List<ClassCourseDto> listClassCourses(Long userId);

    WeekScheduleDto getWeekSchedule(String className, Integer week);
}
