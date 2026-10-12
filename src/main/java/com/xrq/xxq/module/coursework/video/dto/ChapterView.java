package com.xrq.xxq.module.coursework.video.dto;

import java.util.List;

import lombok.Data;

/** 章视图：章 -> 节列表。 */
@Data
public class ChapterView {

    private Long id;
    private String title;
    private List<SectionView> sections;
}
