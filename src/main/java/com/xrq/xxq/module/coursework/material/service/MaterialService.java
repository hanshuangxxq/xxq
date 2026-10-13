package com.xrq.xxq.module.coursework.material.service;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.coursework.material.dto.MaterialBrowseView;
import com.xrq.xxq.module.coursework.material.dto.MaterialSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialUpdateRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialView;

/** 课程资料服务:上传登记(二选一入口)、元数据编辑、移动、软删、逐层浏览。 */
public interface MaterialService {

    MaterialView upload(Long teacherUserId, MaterialSaveRequest req, MultipartFile file);

    MaterialView update(Long teacherUserId, Long id, MaterialUpdateRequest req);

    /** 移动资料到目标文件夹(folderId null = 根目录;文件夹须属同一授课组)。 */
    MaterialView move(Long teacherUserId, Long id, Long folderId);

    void delete(Long teacherUserId, Long id);

    /** 逐层浏览(folderId 空 = 根层;教师查归属 / 学生查可见)。 */
    MaterialBrowseView browse(Long userId, String userType, Long teachInfoId, Long folderId);
}
