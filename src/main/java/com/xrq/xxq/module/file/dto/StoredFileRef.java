package com.xrq.xxq.module.file.dto;

import com.xrq.xxq.module.file.entity.FileBizEnum;

/**
 * 产物引用：文件落盘完成后的交付物，调用方把 {@code storedPath} 写入自己的业务表。
 *
 * @param storedPath   相对存储根的路径，形如 {@code objects/{biz}/{sha256}{ext}}。
 *                     <b>内容寻址</b>：路径由内容摘要决定，故同一路径的字节永不改变
 *                     （这是 ETag 可免服务端求值、以及「业务删记录不删文件」两个设计的共同依据）。
 * @param originalName 展示名（下载时作为 {@code Content-Disposition} 文件名）
 * @param size         字节数
 * @param sha256       小写十六进制内容摘要
 * @param biz          归属业务目录
 */
public record StoredFileRef(String storedPath, String originalName, long size, String sha256,
                            FileBizEnum biz) {
}
