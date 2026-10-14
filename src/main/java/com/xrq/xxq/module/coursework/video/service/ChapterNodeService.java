package com.xrq.xxq.module.coursework.video.service;

import com.xrq.xxq.module.coursework.video.dto.ChapterSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.ChapterTreeView;
import com.xrq.xxq.module.coursework.video.dto.ChapterView;
import com.xrq.xxq.module.coursework.video.dto.NodeRenameRequest;
import com.xrq.xxq.module.coursework.video.dto.SectionSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.SectionView;
import com.xrq.xxq.module.coursework.video.entity.CourseChapterNode;

/**
 * 章节树服务：章/节的增删改与整棵树查询（树 = 章 -> 节 -> 视频）。
 * 章/节仅归属教师可写；树教师（归属）/学生（可见）可读；未挂节视频（公共区）见 GET /coursework/videos/common。
 */
public interface ChapterNodeService {

    ChapterView createChapter(Long teacherUserId, ChapterSaveRequest req);

    void renameChapter(Long teacherUserId, Long id, NodeRenameRequest req);

    /** 级联软删：章 + 其下节 + 节下视频（文件本体交 FileMaintenanceTask 回收）。 */
    void deleteChapter(Long teacherUserId, Long id);

    SectionView createSection(Long teacherUserId, SectionSaveRequest req);

    void renameSection(Long teacherUserId, Long id, NodeRenameRequest req);

    /** 级联软删：节 + 其下视频。 */
    void deleteSection(Long teacherUserId, Long id);

    /** 整棵树（章/节/视频；教师归属校验 / 学生可见性校验）。 */
    ChapterTreeView tree(Long userId, String userType, Long teachInfoId);

    /** 视频模块用：节存在、类型为 SECTION、归属该教师，返回节点（其 teachInfoId 即锚点行 id）。 */
    CourseChapterNode requireOwnedSection(Long nodeId, Long teacherUserId);
}
