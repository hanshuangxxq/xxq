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
import com.xrq.xxq.module.coursework.material.dto.MaterialSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialUpdateRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialView;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterial;
import com.xrq.xxq.module.coursework.material.mapper.CourseMaterialMapper;
import com.xrq.xxq.module.coursework.material.service.MaterialService;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl extends ServiceImpl<CourseMaterialMapper, CourseMaterial>
        implements MaterialService {

    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;

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
        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file, FileBizEnum.COURSE_MATERIAL, true);

        CourseMaterial m = new CourseMaterial();
        m.setTeachInfoId(anchor.getId());
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
    public void delete(Long teacherUserId, Long id) {
        CourseMaterial m = requireOwnedMaterial(id, teacherUserId);
        baseMapper.deleteById(m.getId());
    }

    @Override
    public List<MaterialView> listForTeacher(Long teacherUserId, Long teachInfoId) {
        TeachInfo anchor = groupResolver.requireOwnedAnchor(teachInfoId, teacherUserId);
        return listByAnchor(anchor.getId());
    }

    @Override
    public List<MaterialView> listForStudent(Long studentUserId, Long teachInfoId) {
        groupResolver.assertVisibleToStudent(teachInfoId, studentUserId);
        TeachInfo anchor = groupResolver.requireAnchor(teachInfoId);
        return listByAnchor(anchor.getId());
    }

    private List<MaterialView> listByAnchor(Long anchorId) {
        return baseMapper.selectList(new LambdaQueryWrapper<CourseMaterial>()
                        .eq(CourseMaterial::getTeachInfoId, anchorId)
                        .orderByDesc(CourseMaterial::getCreateTime)
                        .orderByDesc(CourseMaterial::getId)).stream()
                .map(this::toView).toList();
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
