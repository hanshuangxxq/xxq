package com.xrq.xxq.module.coursework.video.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 视频登记请求（multipart 的 data 部分；文件本体先经 file 模块分片上传，或随请求整传）。 */
@Data
public class VideoSaveRequest {

    @NonNull
    private Long teachInfoId;

    @NonNull
    private String title;

    private String description;

    /** 分片产物路径（与 multipart file 二选一；视频一般走分片）。 */
    private String filePath;

    /** 原始文件名（可空）。 */
    private String fileOriginal;

    /** 时长秒：客户端解析 MP4 后回传，可空。 */
    private Integer durationSec;

    private Integer sortNo;
}
