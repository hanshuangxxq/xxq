package com.xrq.xxq.module.coursework.material.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.coursework.material.entity.CourseMaterialFolder;
import lombok.Data;

/** 资料文件夹视图（单层列表 / 面包屑 path / 移动选择器共用）。 */
@Data
public class MaterialFolderView {

    private Long id;
    private Long parentId;
    private String title;
    private LocalDateTime createTime;

    /** 实体 -> 视图（单一口径，与 VideoView.of 同款）。 */
    public static MaterialFolderView of(CourseMaterialFolder folder) {
        MaterialFolderView v = new MaterialFolderView();
        v.setId(folder.getId());
        v.setParentId(folder.getParentId());
        v.setTitle(folder.getTitle());
        v.setCreateTime(folder.getCreateTime());
        return v;
    }
}
