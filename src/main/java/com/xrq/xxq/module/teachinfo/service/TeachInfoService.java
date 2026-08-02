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

    WeekScheduleDto getWeekSchedule(Long userId, Integer week);

    /**
     * 学生可见的授课安排 ID 列表（常规班 + 公选课班），供考试查询等场景复用。
     * <p>常规班按学生所在班级名 FIND_IN_SET；公选课班按选课成员 -> 选课班 -> teachInfoId。
     */
    List<Long> listMyTeachInfoIds(Long studentUserId);
}
