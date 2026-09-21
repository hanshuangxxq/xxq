package com.xrq.xxq.module.coursework.assignment.service;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentSaveRequest;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentView;

/**
 * 课程作业服务：教师 CRUD + 状态机（DRAFT→PUBLISHED→CLOSED）+ 双视角列表/详情。
 * 所有 teachInfoId 入参接受组内任意行，服务内经 CourseGroupResolver 归一到锚点。
 */
public interface AssignmentService {

    /** 创建作业（DRAFT 或直接 PUBLISHED；附件可选，file 与 filePath 二选一）。 */
    AssignmentView create(Long teacherUserId, AssignmentSaveRequest req, MultipartFile file);

    /** 修改（JSON；不含附件）。PUBLISHED 后 deadline 只允许延长、totalScore 不可改；CLOSED 只读。 */
    AssignmentView update(Long teacherUserId, Long id, AssignmentSaveRequest req);

    /** 替换附件（POST multipart 或分片产物路径，二选一）。 */
    AssignmentView replaceAttachment(Long teacherUserId, Long id, String filePath, String fileOriginal,
                                     MultipartFile file);

    /** 删除（仅 DRAFT；有提交记录 409）。软删。 */
    void delete(Long teacherUserId, Long id);

    /** 发布：DRAFT → PUBLISHED，通知授课组学生。 */
    AssignmentView publish(Long teacherUserId, Long id);

    /** 关闭：PUBLISHED → CLOSED，停止提交。 */
    AssignmentView close(Long teacherUserId, Long id);

    /** 教师视角分页列表（含提交/已批改计数）。 */
    PageResult<AssignmentView> listForTeacher(Long teacherUserId, Long teachInfoId, PageQuery pageQuery);

    /** 学生视角分页列表（仅 PUBLISHED/CLOSED，含我的提交摘要）。 */
    PageResult<AssignmentView> listForStudent(Long studentUserId, Long teachInfoId, PageQuery pageQuery);

    /** 教师视角详情（含统计）。 */
    AssignmentView detailForTeacher(Long teacherUserId, Long id);

    /** 学生视角详情（DRAFT 对学生按 404 处理；含我的提交摘要）。 */
    AssignmentView detailForStudent(Long studentUserId, Long id);
}
