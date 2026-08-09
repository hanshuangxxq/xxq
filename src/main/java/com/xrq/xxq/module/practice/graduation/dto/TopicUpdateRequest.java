package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 毕业设计选题更新请求（部分更新，字段可空）。
 */
@Data
public class TopicUpdateRequest {

    private String title;
    private String description;
    private String requirements;
    private Integer capacity;
}
