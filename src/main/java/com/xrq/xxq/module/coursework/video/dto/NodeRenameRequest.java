package com.xrq.xxq.module.coursework.video.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 章/节改名请求（章与节共用）。 */
@Data
public class NodeRenameRequest {

    @NonNull
    private String title;
}
