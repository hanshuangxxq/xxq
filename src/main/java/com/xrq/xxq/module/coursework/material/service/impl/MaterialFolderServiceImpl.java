package com.xrq.xxq.module.coursework.material.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderRenameRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderView;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterial;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterialFolder;
import com.xrq.xxq.module.coursework.material.mapper.CourseMaterialFolderMapper;
import com.xrq.xxq.module.coursework.material.mapper.CourseMaterialMapper;
import com.xrq.xxq.module.coursework.material.service.MaterialFolderService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaterialFolderServiceImpl
        extends ServiceImpl<CourseMaterialFolderMapper, CourseMaterialFolder>
        implements MaterialFolderService {

    private final CourseGroupResolver groupResolver;
    private final CourseMaterialMapper materialMapper;

    @Override
    public MaterialFolderView create(Long teacherUserId, MaterialFolderSaveRequest req) {
        if (req.getTeachInfoId() == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        Long anchorId = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId).getId();
        Long parentId = null;
        if (req.getParentId() != null) {
            parentId = requireInAnchor(req.getParentId(), anchorId).getId();
        }
        CourseMaterialFolder f = new CourseMaterialFolder();
        f.setTeachInfoId(anchorId);
        f.setParentId(parentId);
        f.setTitle(requireTitle(req.getTitle()));
        f.setCreateTime(LocalDateTime.now());
        f.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(f);
        return MaterialFolderView.of(f);
    }

    @Override
    @Transactional
    public void rename(Long teacherUserId, Long id, MaterialFolderRenameRequest req) {
        CourseMaterialFolder f = requireOwnedFolder(id, teacherUserId);
        f.setTitle(requireTitle(req.getTitle()));
        f.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(f);
    }

    @Override
    @Transactional
    public void deleteCascade(Long teacherUserId, Long id) {
        CourseMaterialFolder f = requireOwnedFolder(id, teacherUserId);
        // 文件夹元数据量小,一次性读进内存算子树;资料行不预载,直接按 folderId 集合删
        List<Long> ids = collectSubtreeIds(allFoldersOfAnchor(f.getTeachInfoId()), id);
        materialMapper.delete(new LambdaQueryWrapper<CourseMaterial>()
                .eq(CourseMaterial::getTeachInfoId, f.getTeachInfoId())
                .in(CourseMaterial::getFolderId, ids));
        baseMapper.deleteBatchIds(ids);
    }

    @Override
    public List<MaterialFolderView> listLevel(Long userId, String userType, Long teachInfoId, Long parentId) {
        Long anchorId = resolveReadableAnchor(userId, userType, teachInfoId);
        LambdaQueryWrapper<CourseMaterialFolder> qw = new LambdaQueryWrapper<CourseMaterialFolder>()
                .eq(CourseMaterialFolder::getTeachInfoId, anchorId)
                .orderByAsc(CourseMaterialFolder::getId);
        if (parentId == null) {
            qw.isNull(CourseMaterialFolder::getParentId);
        } else {
            requireInAnchor(parentId, anchorId);
            qw.eq(CourseMaterialFolder::getParentId, parentId);
        }
        return baseMapper.selectList(qw).stream().map(MaterialFolderView::of).toList();
    }

    @Override
    public CourseMaterialFolder requireInAnchor(Long folderId, Long anchorId) {
        CourseMaterialFolder f = baseMapper.selectById(folderId);
        if (f == null || !f.getTeachInfoId().equals(anchorId)) {
            throw new BusinessException(404, "文件夹不存在");
        }
        return f;
    }

    /**
     * 面包屑链(纯函数,供单测):根 -> 当前(含当前文件夹本身),currentId 为 null 返回空表。
     * 父链断裂(并发删除/数据异常)时截断 —— 不死循环、不抛错。
     */
    static List<MaterialFolderView> buildPath(List<CourseMaterialFolder> all, Long currentId) {
        Map<Long, CourseMaterialFolder> byId = new HashMap<>();
        for (CourseMaterialFolder f : all) {
            byId.put(f.getId(), f);
        }
        LinkedList<MaterialFolderView> chain = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        CourseMaterialFolder cur = currentId == null ? null : byId.get(currentId);
        while (cur != null && seen.add(cur.getId())) {
            chain.addFirst(MaterialFolderView.of(cur));
            cur = cur.getParentId() == null ? null : byId.get(cur.getParentId());
        }
        return chain;
    }

    /** 子树全部 id 含 rootId(BFS,纯函数,供单测)。 */
    static List<Long> collectSubtreeIds(List<CourseMaterialFolder> all, Long rootId) {
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (CourseMaterialFolder f : all) {
            if (f.getParentId() != null) {
                childrenOf.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f.getId());
            }
        }
        List<Long> ids = new ArrayList<>();
        Deque<Long> frontier = new ArrayDeque<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            Long cur = frontier.poll();
            ids.add(cur);
            frontier.addAll(childrenOf.getOrDefault(cur, List.of()));
        }
        return ids;
    }

    /** 锚点下全部未删文件夹(id 升序)。文件夹是元数据,量小,面包屑/子树计算都基于它。 */
    List<CourseMaterialFolder> allFoldersOfAnchor(Long anchorId) {
        return baseMapper.selectList(new LambdaQueryWrapper<CourseMaterialFolder>()
                .eq(CourseMaterialFolder::getTeachInfoId, anchorId)
                .orderByAsc(CourseMaterialFolder::getId));
    }

    /** 读接口的双视角锚点解析:教师查归属 / 学生查可见(与 ChapterNodeServiceImpl.tree 同口径)。 */
    Long resolveReadableAnchor(Long userId, String userType, Long teachInfoId) {
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            return groupResolver.requireOwnedAnchor(teachInfoId, userId).getId();
        }
        groupResolver.assertVisibleToStudent(teachInfoId, userId);
        return groupResolver.requireAnchor(teachInfoId).getId();
    }

    private CourseMaterialFolder requireOwnedFolder(Long id, Long teacherUserId) {
        CourseMaterialFolder f = baseMapper.selectById(id);
        if (f == null) {
            throw new BusinessException(404, "文件夹不存在");
        }
        groupResolver.requireOwnedAnchor(f.getTeachInfoId(), teacherUserId);
        return f;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "文件夹名不能为空");
        }
        return title.trim();
    }
}
