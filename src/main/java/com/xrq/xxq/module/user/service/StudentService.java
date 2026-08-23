package com.xrq.xxq.module.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.user.dto.StudentDto;
import com.xrq.xxq.module.user.dto.UpdateStudentRequest;
import com.xrq.xxq.module.user.entity.user.Student;

import java.util.List;

/**
 * 学生业务服务，继承 MyBatis Plus IService 提供通用 CRUD。
 *
 * @类名 StudentService
 * @Date 2026/6/22
 */
public interface StudentService extends IService<Student> {

    PageResult<StudentDto> queryStudents(Long gradeId, List<Long> classIds, List<Long> majorIds, Boolean unassigned, String name, PageQuery pageQuery);

    Boolean updateStudentInfo(Long studentId, UpdateStudentRequest request);
}
