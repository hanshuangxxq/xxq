package com.xrq.xxq.module.coursework.material.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.CourseworkFileSupport;
import com.xrq.xxq.module.coursework.material.dto.MaterialBrowseView;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderView;
import com.xrq.xxq.module.coursework.material.dto.MaterialSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialUpdateRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialView;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterial;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterialFolder;
import com.xrq.xxq.module.coursework.material.mapper.CourseMaterialFolderMapper;
import com.xrq.xxq.module.coursework.material.mapper.CourseMaterialMapper;
import com.xrq.xxq.module.coursework.material.service.MaterialFolderService;
import com.xrq.xxq.module.coursework.material.service.MaterialService;
import com.xrq.xxq.module.coursework.video.service.VideoService;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/** 课程资料服务实现:平铺列表已下线,读走逐层 browse。 */
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl extends ServiceImpl<CourseMaterialMapper, CourseMaterial>
        implements MaterialService {

    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;
    private final MaterialFolderService materialFolderService;
    private final CourseMaterialFolderMapper folderMapper;
    private final VideoService videoService;

    /** 从存储路径提取扩展名（含点，小写）；无扩展名返回空串。包级可见供单测。 */
    static String extOf(String storedPath) {
        int idx = storedPath == null ? -1 : storedPath.lastIndexOf('.');
        if (idx < 0 || idx == storedPath.length() - 1) {
            return "";
        }
        return storedPath.substring(idx).toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public MaterialView upload(Long teacherUserId, MaterialSaveRequest req, MultipartFile file) {
        if (req.getTeachInfoId() == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        TeachInfo anchor = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId);
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(400, "资料标题不能为空");
        }
        Long folderId = null;
        if (req.getFolderId() != null) {
            folderId = materialFolderService.requireInAnchor(req.getFolderId(), anchor.getId()).getId();
        }
        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file, FileBizEnum.COURSE_MATERIAL, true);

        CourseMaterial m = new CourseMaterial();
        m.setTeachInfoId(anchor.getId());
        m.setFolderId(folderId);
        m.setTitle(req.getTitle().trim());
        m.setDescription(req.getDescription());
        m.setFileName(ref.storedPath());
        m.setFileOriginal((req.getFileOriginal() == null || req.getFileOriginal().isBlank())
                ? ref.originalName() : req.getFileOriginal().trim());
        m.setFileExt(extOf(ref.storedPath()));
        m.setSizeBytes(ref.size());
        m.setTeacherId(teacherUserId);
        m.setCreateTime(LocalDateTime.now());
        baseMapper.insert(m);
        return toView(m);
    }

    @Override
    @Transactional
    public MaterialView update(Long teacherUserId, Long id, MaterialUpdateRequest req) {
        CourseMaterial m = requireOwnedMaterial(id, teacherUserId);
        if (req.getTitle() != null) {
            if (req.getTitle().isBlank()) {
                throw new BusinessException(400, "资料标题不能为空");
            }
            m.setTitle(req.getTitle().trim());
        }
        if (req.getDescription() != null) {
            m.setDescription(req.getDescription());
        }
        baseMapper.updateById(m);
        return toView(m);
    }

    @Override
    @Transactional
    public MaterialView move(Long teacherUserId, Long id, Long folderId) {
        CourseMaterial m = requireOwnedMaterial(id, teacherUserId);
        if (folderId != null) {
            materialFolderService.requireInAnchor(folderId, m.getTeachInfoId());
        }
        m.setFolderId(folderId);
        baseMapper.updateById(m);
        return toView(m);
    }

    @Override
    @Transactional
    public void delete(Long teacherUserId, Long id) {
        CourseMaterial m = requireOwnedMaterial(id, teacherUserId);
        baseMapper.deleteById(m.getId());
    }

    @Override
    public MaterialBrowseView browse(Long userId, String userType, Long teachInfoId, Long folderId) {
        Long anchorId = resolveReadableAnchor(userId, userType, teachInfoId);
        if (folderId != null) {
            materialFolderService.requireInAnchor(folderId, anchorId);
        }
        MaterialBrowseView v = new MaterialBrowseView();
        v.setTeachInfoId(anchorId);
        v.setPath(folderId == null
                ? List.of()
                : MaterialFolderServiceImpl.buildPath(allFoldersOfAnchor(anchorId), folderId));

        LambdaQueryWrapper<CourseMaterialFolder> fq = new LambdaQueryWrapper<CourseMaterialFolder>()
                .eq(CourseMaterialFolder::getTeachInfoId, anchorId)
                .orderByAsc(CourseMaterialFolder::getId);
        if (folderId == null) {
            fq.isNull(CourseMaterialFolder::getParentId);
        } else {
            fq.eq(CourseMaterialFolder::getParentId, folderId);
        }
        v.setFolders(folderMapper.selectList(fq).stream().map(MaterialFolderView::of).toList());

        LambdaQueryWrapper<CourseMaterial> mq = new LambdaQueryWrapper<CourseMaterial>()
                .eq(CourseMaterial::getTeachInfoId, anchorId)
                .orderByDesc(CourseMaterial::getCreateTime)
                .orderByDesc(CourseMaterial::getId);
        if (folderId == null) {
            mq.isNull(CourseMaterial::getFolderId);
        } else {
            mq.eq(CourseMaterial::getFolderId, folderId);
        }
        v.setMaterials(baseMapper.selectList(mq).stream().map(this::toView).toList());
        v.setCommonVideoCount(folderId == null ? videoService.countCommon(anchorId) : null);
        return v;
    }

    /** 读接口的双视角锚点解析:教师查归属 / 学生查可见(与 ChapterNodeServiceImpl.tree 同口径)。 */
    private Long resolveReadableAnchor(Long userId, String userType, Long teachInfoId) {
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            return groupResolver.requireOwnedAnchor(teachInfoId, userId).getId();
        }
        groupResolver.assertVisibleToStudent(teachInfoId, userId);
        return groupResolver.requireAnchor(teachInfoId).getId();
    }

    /** 锚点下全部未删文件夹(id 升序);文件夹是元数据,量小,面包屑计算基于它。 */
    private List<CourseMaterialFolder> allFoldersOfAnchor(Long anchorId) {
        return folderMapper.selectList(new LambdaQueryWrapper<CourseMaterialFolder>()
                .eq(CourseMaterialFolder::getTeachInfoId, anchorId)
                .orderByAsc(CourseMaterialFolder::getId));
    }

    private CourseMaterial requireOwnedMaterial(Long id, Long teacherUserId) {
        CourseMaterial m = baseMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "资料不存在");
        }
        groupResolver.requireOwnedAnchor(m.getTeachInfoId(), teacherUserId);
        return m;
    }

    private MaterialView toView(CourseMaterial m) {
        MaterialView v = new MaterialView();
        v.setId(m.getId());
        v.setTeachInfoId(m.getTeachInfoId());
        v.setFolderId(m.getFolderId());
        v.setTitle(m.getTitle());
        v.setDescription(m.getDescription());
        v.setFileName(m.getFileName());
        v.setFileOriginal(m.getFileOriginal());
        v.setFileExt(m.getFileExt());
        v.setSizeBytes(m.getSizeBytes());
        v.setCreateTime(m.getCreateTime());
        return v;
    }
}
