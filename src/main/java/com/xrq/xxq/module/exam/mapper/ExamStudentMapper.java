package com.xrq.xxq.module.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.exam.entity.ExamStudent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考试学生名单 Mapper。
 */
@Mapper
public interface ExamStudentMapper extends BaseMapper<ExamStudent> {
}
