package com.xrq.xxq.module.coursework.material.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.coursework.material.dto.MaterialSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialUpdateRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialView;

/** 课程资料服务：上传登记（二选一入口）、元数据编辑、软删、列表。 */
public interface MaterialService {

    MaterialView upload(Long teacherUserId, MaterialSaveRequest req, MultipartFile file);

    MaterialView update(Long teacherUserId, Long id, MaterialUpdateRequest req);

    void delete(Long teacherUserId, Long id);

    List<MaterialView> listForTeacher(Long teacherUserId, Long teachInfoId);

    List<MaterialView> listForStudent(Long studentUserId, Long teachInfoId);
}
