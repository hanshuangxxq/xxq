package com.xrq.xxq.module.coursework.video.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.video.dto.ChapterSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.ChapterTreeView;
import com.xrq.xxq.module.coursework.video.dto.ChapterView;
import com.xrq.xxq.module.coursework.video.dto.NodeRenameRequest;
import com.xrq.xxq.module.coursework.video.dto.SectionSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.SectionView;
import com.xrq.xxq.module.coursework.video.dto.VideoView;
import com.xrq.xxq.module.coursework.video.entity.ChapterNodeTypeEnum;
import com.xrq.xxq.module.coursework.video.entity.CourseChapterNode;
import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import com.xrq.xxq.module.coursework.video.mapper.ChapterNodeMapper;
import com.xrq.xxq.module.coursework.video.mapper.CourseVideoMapper;
import com.xrq.xxq.module.coursework.video.service.ChapterNodeService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChapterNodeServiceImpl extends ServiceImpl<ChapterNodeMapper, CourseChapterNode> implements ChapterNodeService {

    private final CourseGroupResolver groupResolver;
    private final CourseVideoMapper videoMapper;

    @Override
    public ChapterView createChapter(Long teacherUserId, ChapterSaveRequest req) {
        if (req.getTeachInfoId() == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        Long anchorId = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId).getId();
        CourseChapterNode node = newNode(anchorId, null, ChapterNodeTypeEnum.CHAPTER, requireTitle(req.getTitle()));
        baseMapper.insert(node);
        return toChapterView(node);
    }

    @Override
    @Transactional
    public void renameChapter(Long teacherUserId, Long id, NodeRenameRequest req) {
        CourseChapterNode node = requireOwnedNode(id, teacherUserId);
        if (node.getNodeType() != ChapterNodeTypeEnum.CHAPTER) {
            throw new BusinessException(400, "目标节点不是章");
        }
        node.setTitle(requireTitle(req.getTitle()));
        node.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(node);
    }

    @Override
    @Transactional
    public void deleteChapter(Long teacherUserId, Long id) {
        CourseChapterNode node = requireOwnedNode(id, teacherUserId);
        if (node.getNodeType() != ChapterNodeTypeEnum.CHAPTER) {
            throw new BusinessException(400, "目标节点不是章");
        }
        // 级联软删：节下视频 -> 节 -> 章（文件为内容寻址产物，交 FileMaintenanceTask 回收）
        List<Long> sectionIds = baseMapper.selectList(new LambdaQueryWrapper<CourseChapterNode>()
                        .eq(CourseChapterNode::getParentId, id)).stream()
                .map(CourseChapterNode::getId).toList();
        if (!sectionIds.isEmpty()) {
            videoMapper.delete(new LambdaQueryWrapper<CourseVideo>()
                    .in(CourseVideo::getNodeId, sectionIds));
            baseMapper.deleteBatchIds(sectionIds);
        }
        baseMapper.deleteById(id);
    }

    @Override
    public SectionView createSection(Long teacherUserId, SectionSaveRequest req) {
        if (req.getChapterId() == null) {
            throw new BusinessException(400, "所属章不能为空");
        }
        CourseChapterNode chapter = baseMapper.selectById(req.getChapterId());
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        if (chapter.getNodeType() != ChapterNodeTypeEnum.CHAPTER) {
            throw new BusinessException(400, "目标节点不是章");
        }
        groupResolver.requireOwnedAnchor(chapter.getTeachInfoId(), teacherUserId);
        CourseChapterNode node = newNode(chapter.getTeachInfoId(), chapter.getId(),
                ChapterNodeTypeEnum.SECTION, requireTitle(req.getTitle()));
        baseMapper.insert(node);
        return toSectionView(node);
    }

    @Override
    @Transactional
    public void renameSection(Long teacherUserId, Long id, NodeRenameRequest req) {
        CourseChapterNode node = requireOwnedNode(id, teacherUserId);
        if (node.getNodeType() != ChapterNodeTypeEnum.SECTION) {
            throw new BusinessException(400, "目标节点不是节");
        }
        node.setTitle(requireTitle(req.getTitle()));
        node.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(node);
    }

    @Override
    @Transactional
    public void deleteSection(Long teacherUserId, Long id) {
        CourseChapterNode node = requireOwnedNode(id, teacherUserId);
        if (node.getNodeType() != ChapterNodeTypeEnum.SECTION) {
            throw new BusinessException(400, "目标节点不是节");
        }
        // 级联软删：节下视频 -> 节
        videoMapper.delete(new LambdaQueryWrapper<CourseVideo>()
                .eq(CourseVideo::getNodeId, id));
        baseMapper.deleteById(id);
    }

    @Override
    public ChapterTreeView tree(Long userId, String userType, Long teachInfoId) {
        Long anchorId;
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            anchorId = groupResolver.requireOwnedAnchor(teachInfoId, userId).getId();
        } else {
            groupResolver.assertVisibleToStudent(teachInfoId, userId);
            anchorId = groupResolver.requireAnchor(teachInfoId).getId();
        }
        List<CourseChapterNode> nodes = baseMapper.selectList(new LambdaQueryWrapper<CourseChapterNode>()
                .eq(CourseChapterNode::getTeachInfoId, anchorId)
                .orderByAsc(CourseChapterNode::getId));
        List<CourseVideo> videos = videoMapper.selectList(new LambdaQueryWrapper<CourseVideo>()
                .eq(CourseVideo::getTeachInfoId, anchorId)
                .orderByAsc(CourseVideo::getId));
        return assemble(nodes, videos);
    }

    @Override
    public CourseChapterNode requireOwnedSection(Long nodeId, Long teacherUserId) {
        if (nodeId == null) {
            throw new BusinessException(400, "视频必须挂载到小节");
        }
        CourseChapterNode node = baseMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(404, "小节不存在");
        }
        if (node.getNodeType() != ChapterNodeTypeEnum.SECTION) {
            throw new BusinessException(400, "目标节点不是小节");
        }
        groupResolver.requireOwnedAnchor(node.getTeachInfoId(), teacherUserId);
        return node;
    }

    /**
     * 树组装（纯函数，供单测）：nodes/videos 需已按 id 升序。
     * 容错：父章缺失的孤儿节不挂树；挂在缺失节点上的视频与未挂节视频一律落入公共区，不静默消失。
     */
    static ChapterTreeView assemble(List<CourseChapterNode> nodes, List<CourseVideo> videos) {
        Map<Long, ChapterView> chapters = new LinkedHashMap<>();
        Map<Long, SectionView> sections = new LinkedHashMap<>();
        for (CourseChapterNode n : nodes) {
            if (n.getNodeType() == ChapterNodeTypeEnum.CHAPTER) {
                chapters.put(n.getId(), toChapterView(n));
            }
        }
        for (CourseChapterNode n : nodes) {
            if (n.getNodeType() == ChapterNodeTypeEnum.SECTION && n.getParentId() != null
                    && chapters.containsKey(n.getParentId())) {
                SectionView sv = toSectionView(n);
                sections.put(n.getId(), sv);
                chapters.get(n.getParentId()).getSections().add(sv);
            }
        }
        List<VideoView> common = new ArrayList<>();
        for (CourseVideo v : videos) {
            SectionView bucket = v.getNodeId() == null ? null : sections.get(v.getNodeId());
            if (bucket == null) {
                common.add(VideoView.of(v));
            } else {
                bucket.getVideos().add(VideoView.of(v));
            }
        }
        ChapterTreeView tree = new ChapterTreeView();
        tree.setChapters(new ArrayList<>(chapters.values()));
        tree.setCommonVideos(common);
        return tree;
    }

    private CourseChapterNode requireOwnedNode(Long id, Long teacherUserId) {
        CourseChapterNode node = baseMapper.selectById(id);
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }
        groupResolver.requireOwnedAnchor(node.getTeachInfoId(), teacherUserId);
        return node;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        return title.trim();
    }

    private static CourseChapterNode newNode(Long anchorId, Long parentId, ChapterNodeTypeEnum type, String title) {
        CourseChapterNode node = new CourseChapterNode();
        node.setTeachInfoId(anchorId);
        node.setParentId(parentId);
        node.setNodeType(type);
        node.setTitle(title);
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        return node;
    }

    private static ChapterView toChapterView(CourseChapterNode n) {
        ChapterView view = new ChapterView();
        view.setId(n.getId());
        view.setTitle(n.getTitle());
        view.setSections(new ArrayList<>());
        return view;
    }

    private static SectionView toSectionView(CourseChapterNode n) {
        SectionView view = new SectionView();
        view.setId(n.getId());
        view.setTitle(n.getTitle());
        view.setVideos(new ArrayList<>());
        return view;
    }
}
