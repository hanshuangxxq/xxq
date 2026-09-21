package com.xrq.xxq.module.coursework.announcement.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.coursework.announcement.dto.AnnouncementSaveRequest;
import com.xrq.xxq.module.coursework.announcement.dto.AnnouncementView;
import com.xrq.xxq.module.coursework.announcement.entity.CourseAnnouncement;
import com.xrq.xxq.module.coursework.announcement.mapper.CourseAnnouncementMapper;
import com.xrq.xxq.module.coursework.announcement.service.AnnouncementService;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.notification.notice.CourseworkNoticeScenes;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl extends ServiceImpl<CourseAnnouncementMapper, CourseAnnouncement>
        implements AnnouncementService {

    private final CourseGroupResolver groupResolver;
    private final CourseInfoResolver courseInfoResolver;
    private final CourseworkNoticeScenes courseworkNoticeScenes;

    @Override
    @Transactional
    public AnnouncementView create(Long teacherUserId, AnnouncementSaveRequest req) {
        if (req.getTeachInfoId() == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        TeachInfo anchor = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId);
        validate(req);
        CourseAnnouncement a = new CourseAnnouncement();
        a.setTeachInfoId(anchor.getId());
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent().trim());
        a.setTeacherId(teacherUserId);
        a.setCreateTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(a);

        CourseInfoResolver.CourseInfo info =
                courseInfoResolver.resolveOne(anchor.getCourseId(), anchor.getCampaignId());
        String courseName = info != null ? info.getCourseName() : "课程";
        for (Long studentUserId : groupResolver.rosterUserIds(anchor.getId())) {
            courseworkNoticeScenes.announcementPublished(studentUserId, courseName, a.getTitle());
        }
        return toView(a);
    }

    @Override
    @Transactional
    public AnnouncementView update(Long teacherUserId, Long id, AnnouncementSaveRequest req) {
        CourseAnnouncement a = requireOwnedAnnouncement(id, teacherUserId);
        validate(req);
        // 编辑不重复通知：列表页可见最新内容即可
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent().trim());
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(a);
        return toView(a);
    }

    @Override
    @Transactional
    public void delete(Long teacherUserId, Long id) {
        CourseAnnouncement a = requireOwnedAnnouncement(id, teacherUserId);
        baseMapper.deleteById(a.getId());
    }

    @Override
    public List<AnnouncementView> listForTeacher(Long teacherUserId, Long teachInfoId) {
        TeachInfo anchor = groupResolver.requireOwnedAnchor(teachInfoId, teacherUserId);
        return listByAnchor(anchor.getId());
    }

    @Override
    public List<AnnouncementView> listForStudent(Long studentUserId, Long teachInfoId) {
        groupResolver.assertVisibleToStudent(teachInfoId, studentUserId);
        TeachInfo anchor = groupResolver.requireAnchor(teachInfoId);
        return listByAnchor(anchor.getId());
    }

    private List<AnnouncementView> listByAnchor(Long anchorId) {
        return baseMapper.selectList(new LambdaQueryWrapper<CourseAnnouncement>()
                        .eq(CourseAnnouncement::getTeachInfoId, anchorId)
                        .orderByDesc(CourseAnnouncement::getCreateTime)
                        .orderByDesc(CourseAnnouncement::getId)).stream()
                .map(this::toView).toList();
    }

    private CourseAnnouncement requireOwnedAnnouncement(Long id, Long teacherUserId) {
        CourseAnnouncement a = baseMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(404, "公告不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);
        return a;
    }

    private static void validate(AnnouncementSaveRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(400, "公告标题不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(400, "公告内容不能为空");
        }
    }

    private AnnouncementView toView(CourseAnnouncement a) {
        AnnouncementView v = new AnnouncementView();
        v.setId(a.getId());
        v.setTeachInfoId(a.getTeachInfoId());
        v.setTitle(a.getTitle());
        v.setContent(a.getContent());
        v.setTeacherId(a.getTeacherId());
        v.setCreateTime(a.getCreateTime());
        v.setUpdateTime(a.getUpdateTime());
        return v;
    }
}
