package com.xrq.xxq.module.coursework.video.dto;

import java.util.List;

import lombok.Data;

/** 章节树视图：整棵树 + 公共区（未挂节的视频，含存量与孤儿兜底）。 */
@Data
public class ChapterTreeView {

    private List<ChapterView> chapters;
    private List<VideoView> commonVideos;
}
