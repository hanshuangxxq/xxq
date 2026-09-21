package com.xrq.xxq.module.coursework.assignment.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平时分合成结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegularScoreSyncView {

    /** 成功写入平时分的学生数。 */
    private Integer updatedCount;

    /** 成绩已锁定被跳过的学生 user.id。 */
    private List<Long> skippedLockedUserIds;

    /** 无已批改作业被跳过的学生 user.id。 */
    private List<Long> skippedUngradedUserIds;
}
