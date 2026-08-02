package com.xrq.xxq.module.score.dto;

import lombok.Data;

/**
 * 成绩占比配置请求：平时分占比 0-100，期末占比 = 100 - regularRatio。
 */
@Data
public class ScoreConfigRequest {

    private Integer regularRatio; // 平时分占比 0-100
}
