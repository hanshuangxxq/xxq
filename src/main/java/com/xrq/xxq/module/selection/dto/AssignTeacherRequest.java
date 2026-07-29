package com.xrq.xxq.module.selection.dto;

import lombok.Data;

/**
 * 选课分班后为指定选课班分配任课教师的请求。
 * <p>
 * teacherId 为 null 表示取消已分配的教师。
 */
@Data
public class AssignTeacherRequest {
    private Long teacherId;
}
