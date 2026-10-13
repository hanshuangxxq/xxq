package com.xrq.xxq.module.coursework.video.dto;

import java.util.List;

import lombok.Data;

/** 章节树视图:章 -> 节 -> 视频。未挂节视频(公共区)已迁至 GET /coursework/videos/common。 */
@Data
public class ChapterTreeView {

    private List<ChapterView> chapters;
}
