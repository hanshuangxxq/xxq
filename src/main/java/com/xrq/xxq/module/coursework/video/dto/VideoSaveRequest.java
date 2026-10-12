package com.xrq.xxq.module.coursework.video.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 视频登记请求（multipart 的 data 部分；文件本体先经 file 模块分片上传，或随请求整传）。 */
@Data
public class VideoSaveRequest {

    /** 挂载的节节点 id（course_chapter_node 中 SECTION 类型节点；视频必须挂节，公共区只存存量）。 */
    @NonNull
    private Long nodeId;

    @NonNull
    private String title;

    private String description;

    /** 分片产物路径（与 multipart file 二选一；视频一般走分片）。 */
    private String filePath;

    /** 原始文件名（可空）。 */
    private String fileOriginal;

    /** 时长秒：客户端解析 MP4 后回传，可空。 */
    private Integer durationSec;
}
