package com.xrq.xxq.module.coursework.video.dto;

import java.util.List;

import lombok.Data;

/** 节视图：节 -> 视频列表。 */
@Data
public class SectionView {

    private Long id;
    private String title;
    private List<VideoView> videos;
}
