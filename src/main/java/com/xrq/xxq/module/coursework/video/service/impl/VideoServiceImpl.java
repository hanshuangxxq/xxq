package com.xrq.xxq.module.coursework.video.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.CourseworkFileSupport;
import com.xrq.xxq.module.coursework.video.dto.VideoSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoUpdateRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoView;
import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import com.xrq.xxq.module.coursework.video.mapper.CourseVideoMapper;
import com.xrq.xxq.module.coursework.video.service.VideoService;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl extends ServiceImpl<CourseVideoMapper, CourseVideo> implements VideoService {

    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;

    @Override
    @Transactional
    public VideoView register(Long teacherUserId, VideoSaveRequest req, MultipartFile file) {
        if (req.getTeachInfoId() == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        TeachInfo anchor = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId);
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(400, "视频标题不能为空");
        }
        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file, FileBizEnum.COURSE_VIDEO, true);

        CourseVideo v = new CourseVideo();
        v.setTeachInfoId(anchor.getId());
        v.setTitle(req.getTitle().trim());
        v.setDescription(req.getDescription());
        v.setFileName(ref.storedPath());
        v.setFileOriginal((req.getFileOriginal() == null || req.getFileOriginal().isBlank())
                ? ref.originalName() : req.getFileOriginal().trim());
        v.setSha256(ref.sha256());
        v.setSizeBytes(ref.size());
        v.setDurationSec(req.getDurationSec());
        v.setSortNo(req.getSortNo() != null ? req.getSortNo() : 0);
        v.setTeacherId(teacherUserId);
        v.setCreateTime(LocalDateTime.now());
        v.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(v);
        return toView(v);
    }

    @Override
    @Transactional
    public VideoView update(Long teacherUserId, Long id, VideoUpdateRequest req) {
        CourseVideo v = requireOwnedVideo(id, teacherUserId);
        if (req.getTitle() != null) {
            if (req.getTitle().isBlank()) {
                throw new BusinessException(400, "视频标题不能为空");
            }
            v.setTitle(req.getTitle().trim());
        }
        if (req.getDescription() != null) {
            v.setDescription(req.getDescription());
        }
        if (req.getSortNo() != null) {
            v.setSortNo(req.getSortNo());
        }
        if (req.getDurationSec() != null) {
            v.setDurationSec(req.getDurationSec());
        }
        v.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(v);
        return toView(v);
    }

    @Override
    @Transactional
    public void delete(Long teacherUserId, Long id) {
        CourseVideo v = requireOwnedVideo(id, teacherUserId);
        baseMapper.deleteById(v.getId());
    }

    @Override
    public List<VideoView> listForTeacher(Long teacherUserId, Long teachInfoId) {
        TeachInfo anchor = groupResolver.requireOwnedAnchor(teachInfoId, teacherUserId);
        return listByAnchor(anchor.getId());
    }

    @Override
    public List<VideoView> listForStudent(Long studentUserId, Long teachInfoId) {
        groupResolver.assertVisibleToStudent(teachInfoId, studentUserId);
        TeachInfo anchor = groupResolver.requireAnchor(teachInfoId);
        return listByAnchor(anchor.getId());
    }

    @Override
    public VideoView detail(Long userId, String userType, Long id) {
        CourseVideo v = baseMapper.selectById(id);
        if (v == null) {
            throw new BusinessException(404, "视频不存在");
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            groupResolver.requireOwnedAnchor(v.getTeachInfoId(), userId);
        } else {
            groupResolver.assertVisibleToStudent(v.getTeachInfoId(), userId);
        }
        return toView(v);
    }

    private List<VideoView> listByAnchor(Long anchorId) {
        return baseMapper.selectList(new LambdaQueryWrapper<CourseVideo>()
                        .eq(CourseVideo::getTeachInfoId, anchorId)
                        .orderByAsc(CourseVideo::getSortNo)
                        .orderByAsc(CourseVideo::getId)).stream()
                .map(this::toView).toList();
    }

    private CourseVideo requireOwnedVideo(Long id, Long teacherUserId) {
        CourseVideo v = baseMapper.selectById(id);
        if (v == null) {
            throw new BusinessException(404, "视频不存在");
        }
        groupResolver.requireOwnedAnchor(v.getTeachInfoId(), teacherUserId);
        return v;
    }

    private VideoView toView(CourseVideo v) {
        VideoView view = new VideoView();
        view.setId(v.getId());
        view.setTeachInfoId(v.getTeachInfoId());
        view.setTitle(v.getTitle());
        view.setDescription(v.getDescription());
        view.setFileName(v.getFileName());
        view.setFileOriginal(v.getFileOriginal());
        view.setSha256(v.getSha256());
        view.setSizeBytes(v.getSizeBytes());
        view.setDurationSec(v.getDurationSec());
        view.setSortNo(v.getSortNo());
        view.setCreateTime(v.getCreateTime());
        return view;
    }
}
