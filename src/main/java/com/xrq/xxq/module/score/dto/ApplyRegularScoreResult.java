package com.xrq.xxq.module.score.dto;

import java.util.List;

/** 平时分批量写入结果。 */
public record ApplyRegularScoreResult(int updatedCount, List<Long> skippedLockedUserIds) {
}
