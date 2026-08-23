package com.xrq.xxq.module.scheduling.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 规划实体：被求解器分配的一节课（对应一条 teach_info 记录）。
 * <p>
 * 规划变量（求解器赋值）：
 * <ul>
 *   <li>{@link #timeslot} — 分配到哪个时间段（周几 + 起止时间）</li>
 *   <li>{@link #room} — 分配到哪个教室</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@PlanningEntity
public class Lesson {

    /** 规划实体ID，对应 teach_info.id */
    @PlanningId
    private Long id;

    /** 课程ID（FK → course.id） */
    private Long courseId;

    /** 公选课活动ID（FK -> selection_campaign.id；常规课为 NULL） */
    private Long campaignId;

    /** 课程名称（冗余，避免约束计算时查库） */
    private String courseName;

    /** 教师ID（FK → teacher.id） */
    private Long teacherId;

    /** 教师姓名（冗余，避免约束计算时查库） */
    private String teacherName;

    /** 上课学生所属的班级集合（支持合班/重修场景，一个课堂可能有多个班级的学生） */
    private List<StudentGroup> studentGroups = new ArrayList<>();

    /** 上课学生的 userId 集合（用于精确的学生维度冲突检测，覆盖选课班跨班级场景） */
    private Set<Long> studentIds = new HashSet<>();

    /** 课堂实际学生人数（用于教室容量约束；选课班取 SelectionClass.studentCount，避免 sum 误算） */
    private Integer studentCount;

    /** 起始教学周 */
    private Integer startWeek;

    /** 结束教学周 */
    private Integer endWeek;

    /** 学期ID */
    private Long semesterId;

    /** 分配的时间段 */
    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private Timeslot timeslot;

    /** 分配的教室 */
    @PlanningVariable(valueRangeProviderRefs = "roomRange")
    private Room room;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lesson lesson)) return false;
        return Objects.equals(id, lesson.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Lesson(id=" + id + ", courseName=" + courseName + ")";
    }
}
