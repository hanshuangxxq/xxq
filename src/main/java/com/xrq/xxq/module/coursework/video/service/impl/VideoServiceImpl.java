package com.xrq.xxq.module.coursework.video.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.xrq.xxq.module.coursework.video.entity.ChapterNodeTypeEnum;
import com.xrq.xxq.module.coursework.video.entity.CourseChapterNode;
import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import com.xrq.xxq.module.coursework.video.mapper.ChapterNodeMapper;
import com.xrq.xxq.module.coursework.video.mapper.CourseVideoMapper;
import com.xrq.xxq.module.coursework.video.service.ChapterNodeService;
import com.xrq.xxq.module.coursework.video.service.VideoService;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl extends ServiceImpl<CourseVideoMapper, CourseVideo> implements VideoService {

    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;
    private final ChapterNodeService chapterNodeService;
    private final ChapterNodeMapper chapterNodeMapper;

    @Override
    @Transactional
    public VideoView register(Long teacherUserId, VideoSaveRequest req, MultipartFile file) {
        // 视频必须挂节：节的归属校验通过即授课组归属成立，锚点由节反推
        CourseChapterNode section = chapterNodeService.requireOwnedSection(req.getNodeId(), teacherUserId);
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(400, "视频标题不能为空");
        }
        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file, FileBizEnum.COURSE_VIDEO, true);

        CourseVideo v = new CourseVideo();
        v.setTeachInfoId(section.getTeachInfoId());
        v.setNodeId(section.getId());
        v.setTitle(req.getTitle().trim());
        v.setDescription(req.getDescription());
        v.setFileName(ref.storedPath());
        v.setFileOriginal((req.getFileOriginal() == null || req.getFileOriginal().isBlank())
                ? ref.originalName() : req.getFileOriginal().trim());
        v.setSha256(ref.sha256());
        v.setSizeBytes(ref.size());
        v.setDurationSec(req.getDurationSec());
        v.setTeacherId(teacherUserId);
        v.setCreateTime(LocalDateTime.now());
        v.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(v);
        return VideoView.of(v);
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
        if (req.getNodeId() != null) {
            // 改挂节：目标节须归属同一授课组（公共区视频由此归入节；不支持移回公共区）
            CourseChapterNode section = chapterNodeService.requireOwnedSection(req.getNodeId(), teacherUserId);
            if (!section.getTeachInfoId().equals(v.getTeachInfoId())) {
                throw new BusinessException(400, "不能跨授课组移动视频");
            }
            v.setNodeId(section.getId());
        }
        if (req.getDurationSec() != null) {
            v.setDurationSec(req.getDurationSec());
        }
        v.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(v);
        return VideoView.of(v);
    }

    @Override
    @Transactional
    public void delete(Long teacherUserId, Long id) {
        CourseVideo v = requireOwnedVideo(id, teacherUserId);
        baseMapper.deleteById(v.getId());
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
        return VideoView.of(v);
    }

    @Override
    public List<VideoView> listCommon(Long userId, String userType, Long teachInfoId) {
        Long anchorId;
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            anchorId = groupResolver.requireOwnedAnchor(teachInfoId, userId).getId();
        } else {
            groupResolver.assertVisibleToStudent(teachInfoId, userId);
            anchorId = groupResolver.requireAnchor(teachInfoId).getId();
        }
        return listCommonOfAnchor(anchorId).stream().map(VideoView::of).toList();
    }

    @Override
    public long countCommon(Long anchorId) {
        return listCommonOfAnchor(anchorId).size();
    }

    /**
     * 公共区 = 未挂节(nodeId 为 null)或挂在已删/不存在节上的孤儿视频。
     * 与旧章节树 assemble 的兜底语义一致:视频不因章节结构异常而静默消失。
     * 「公共区」是授课组内部"未挂节"的分组 —— 每个视频仍属于且仅属于某一授课组。
     */
    private List<CourseVideo> listCommonOfAnchor(Long anchorId) {
        Set<Long> liveSectionIds = chapterNodeMapper.selectList(new LambdaQueryWrapper<CourseChapterNode>()
                        .eq(CourseChapterNode::getTeachInfoId, anchorId)
                        .eq(CourseChapterNode::getNodeType, ChapterNodeTypeEnum.SECTION)).stream()
                .map(CourseChapterNode::getId).collect(Collectors.toSet());
        return baseMapper.selectList(new LambdaQueryWrapper<CourseVideo>()
                        .eq(CourseVideo::getTeachInfoId, anchorId)
                        .orderByAsc(CourseVideo::getId)).stream()
                .filter(v -> v.getNodeId() == null || !liveSectionIds.contains(v.getNodeId()))
                .toList();
    }

    private CourseVideo requireOwnedVideo(Long id, Long teacherUserId) {
        CourseVideo v = baseMapper.selectById(id);
        if (v == null) {
            throw new BusinessException(404, "视频不存在");
        }
        groupResolver.requireOwnedAnchor(v.getTeachInfoId(), teacherUserId);
        return v;
    }
}
