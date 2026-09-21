package com.xrq.xxq.module.coursework.announcement.service;

import java.util.List;

import com.xrq.xxq.module.coursework.announcement.dto.AnnouncementSaveRequest;
import com.xrq.xxq.module.coursework.announcement.dto.AnnouncementView;

/** 课程公告服务：发布（通知授课组学生）、编辑（不重复通知）、软删、列表。 */
public interface AnnouncementService {

    AnnouncementView create(Long teacherUserId, AnnouncementSaveRequest req);

    AnnouncementView update(Long teacherUserId, Long id, AnnouncementSaveRequest req);

    void delete(Long teacherUserId, Long id);

    List<AnnouncementView> listForTeacher(Long teacherUserId, Long teachInfoId);

    List<AnnouncementView> listForStudent(Long studentUserId, Long teachInfoId);
}
