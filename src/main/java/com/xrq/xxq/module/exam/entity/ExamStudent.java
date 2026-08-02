package com.xrq.xxq.module.exam.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 考试学生名单：补考/重修考生，建考时按不及格名单自动生成。
 * UNIQUE(exam_id, student_user_id) 保证幂等。
 */
@Data
@TableName("exam_student")
public class ExamStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;            // FK -> exam.id
    private Long studentUserId;     // 学生 user.id
    private LocalDateTime createTime;
}
