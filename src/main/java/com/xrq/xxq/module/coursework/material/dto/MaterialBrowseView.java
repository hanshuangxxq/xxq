package com.xrq.xxq.module.coursework.material.dto;

import java.util.List;

import lombok.Data;

/**
 * 资料逐层浏览视图 GET /coursework/materials/browse。
 * commonVideoCount 仅根层（folderId 缺省）返回：未挂节视频数，前端据此决定
 * 「教学视频」虚拟节点显隐，不预拉视频列表。
 */
@Data
public class MaterialBrowseView {

    private Long teachInfoId;                    // 归一后的锚点行 id（前端采纳锚点用）
    private List<MaterialFolderView> path;       // 根 -> 当前层的链，含当前文件夹本身（根层为空表）
    private List<MaterialFolderView> folders;    // 当前层子文件夹（id 升序）
    private List<MaterialView> materials;        // 当前层资料（createTime 倒序）
    private Long commonVideoCount;               // 仅根层非 null
}
