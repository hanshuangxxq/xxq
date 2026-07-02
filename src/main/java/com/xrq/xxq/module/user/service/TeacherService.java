package com.xrq.xxq.module.user.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xrq.xxq.module.user.dto.TeacherDto;
import com.xrq.xxq.module.user.entity.user.Teacher;

public interface TeacherService extends IService<Teacher> {

    /** 查询所有教师（含姓名），用于排课下拉选择。 */
    List<TeacherDto> listTeachers();
}
