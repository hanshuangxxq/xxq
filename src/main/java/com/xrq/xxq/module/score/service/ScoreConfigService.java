package com.xrq.xxq.module.score.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.score.entity.ScoreConfig;

/**
 * 成绩占比配置服务：每条授课安排一行，由任课教师设置平时分占比。
 */
public interface ScoreConfigService extends IService<ScoreConfig> {

    /** 按授课安排查询占比配置，未设置返回 null。 */
    ScoreConfig getByTeachInfo(Long teachInfoId);

    /** 新增或更新占比配置（regularRatio 0-100）。 */
    ScoreConfig upsert(Long teachInfoId, Integer regularRatio, Long userId);
}
