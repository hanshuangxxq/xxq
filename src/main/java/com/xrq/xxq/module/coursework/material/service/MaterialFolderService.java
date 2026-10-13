package com.xrq.xxq.module.coursework.material.service;

import java.util.List;

import com.xrq.xxq.module.coursework.material.dto.MaterialFolderRenameRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderSaveRequest;
import com.xrq.xxq.module.coursework.material.dto.MaterialFolderView;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterialFolder;

/** 课程资料文件夹服务:建/改名/级联删/单层列举;文件夹不可换父级(天然无环)。 */
public interface MaterialFolderService {

    MaterialFolderView create(Long teacherUserId, MaterialFolderSaveRequest req);

    void rename(Long teacherUserId, Long id, MaterialFolderRenameRequest req);

    /** 级联软删整棵子树(后代文件夹 + 其中所有资料行;文件本体交回收任务)。 */
    void deleteCascade(Long teacherUserId, Long id);

    /** 单层子文件夹(parentId 空 = 根层;教师查归属 / 学生查可见)。 */
    List<MaterialFolderView> listLevel(Long userId, String userType, Long teachInfoId, Long parentId);

    /** 文件夹须存在且属该锚点,否则 404(供资料服务校验上传/移动目标)。 */
    CourseMaterialFolder requireInAnchor(Long folderId, Long anchorId);
}
