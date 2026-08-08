package com.xrq.xxq.module.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.user.dto.TeacherDto;
import com.xrq.xxq.module.user.entity.user.Teacher;

public interface TeacherService extends IService<Teacher> {

    /** 查询教师列表（含姓名）。 */
    PageResult<TeacherDto> listTeachers(PageQuery pageQuery);
}
