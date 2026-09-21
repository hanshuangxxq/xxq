package com.xrq.xxq.module.coursework.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** 视频 WS 流式播放配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "coursework.video")
public class VideoStreamProperties {

    /** 单次 read 允许的最大字节数（客户端建议 512KB）。 */
    private long chunkMaxBytes = 1024 * 1024;

    /** 每连接最大在途 read 数（滑动窗口）。 */
    private int maxInflight = 8;

    /** 单连接发送限速（MB/s）。 */
    private int rateLimitMbps = 32;

    /** 全局最大并发连接数。 */
    private int maxConnections = 200;

    /** 头/尾段缓存条目上限（每视频占 2 条：head+tail）。 */
    private int headerCacheEntries = 32;

    /** 头/尾段缓存的字节窗口（各 512KB）。 */
    private int headerCacheBytes = 512 * 1024;
}
