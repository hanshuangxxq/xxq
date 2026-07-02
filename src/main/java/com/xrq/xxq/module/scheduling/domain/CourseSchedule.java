package com.xrq.xxq.module.scheduling.domain;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规划解：排课方案容器。
 * <p>
 * 包含问题事实（时间段、教室）、规划实体（待排课程）、求解得分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@PlanningSolution
public class CourseSchedule {

    /** 方案ID */
    private Long id;

    /** 所有可用时间段 */
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslotRange")
    private List<Timeslot> timeslotList = new ArrayList<>();

    /** 所有可用教室 */
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "roomRange")
    private List<Room> roomList = new ArrayList<>();

    /** 所有学生班级（问题事实） */
    @ProblemFactCollectionProperty
    private List<StudentGroup> studentGroupList = new ArrayList<>();

    /** 所有待排课程 */
    @PlanningEntityCollectionProperty
    private List<Lesson> lessonList = new ArrayList<>();

    /** 规划得分（求解器自动计算并回填） */
    @PlanningScore
    private HardSoftScore score;

    /** 求解状态（非 Timefold 字段，供 API 返回） */
    private String solverStatus;
}
