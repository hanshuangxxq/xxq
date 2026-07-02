package com.xrq.xxq.module.scheduling.constraint;

import java.util.List;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.xrq.xxq.module.scheduling.domain.Lesson;
import com.xrq.xxq.module.scheduling.domain.StudentGroup;

/**
 * 排课约束定义。
 * <p>
 * 硬约束（不可违反）：
 * <ul>
 *   <li>教室冲突：同一时间同一教室不能有两节课</li>
 *   <li>教师冲突：同一时间一位教师不能上两节课</li>
 *   <li>班级冲突：同一时间一个班级不能有两节课</li>
 *   <li>时段限制：被预留给特定课程的时间段不能被其他课程占用</li>
 *   <li>教室容量：上课学生总人数不能超过教室最大容量</li>
 * </ul>
 */
public class CourseScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                roomConflict(factory),
                teacherConflict(factory),
                classConflict(factory),
                timeRestriction(factory),
                roomCapacity(factory)
        };
    }

    /**
     * 硬约束：同一时间 + 同一教室 → 只能有一节课。
     * 一个时间段 + 一个教室只能分配给一个课堂。
     */
    private Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeslot),
                        Joiners.equal(Lesson::getRoom))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    /**
     * 硬约束：同一时间 → 同一教师只能上一节课。
     */
    private Constraint teacherConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeslot),
                        Joiners.equal(Lesson::getTeacherId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher conflict");
    }

    /**
     * 硬约束：同一时间 → 任意班级不能有两节课。
     * 使用交集判断支持合班/重修场景——只要两个课堂共享任意一个班级即构成冲突。
     */
    private Constraint classConflict(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getTimeslot),
                        Joiners.lessThan(Lesson::getId))
                .filter((lesson1, lesson2) -> sharesAnyStudentGroup(lesson1, lesson2))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Class conflict");
    }

    /**
     * 硬约束：被标记为 RESERVED 的时间段仅供对应课程使用。
     * 若某节课占用了预留给其他课程的时段，则产生硬约束惩罚。
     */
    private Constraint timeRestriction(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot().getReservedCourseId() != null
                        && !lesson.getTimeslot().getReservedCourseId().equals(lesson.getCourseId()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Time restriction");
    }

    /**
     * 硬约束：上课学生总人数不超过教室最大容量。
     * 计算每个课堂的 studentGroups 学生总数，超过 room.capacity 即惩罚。
     */
    private Constraint roomCapacity(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(lesson -> lesson.getRoom().getCapacity() > 0
                        && lesson.getRoom().getCapacity() < Integer.MAX_VALUE)
                .filter(lesson -> sumStudentCount(lesson) > lesson.getRoom().getCapacity())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room capacity");
    }

    private static int sumStudentCount(Lesson lesson) {
        List<StudentGroup> groups = lesson.getStudentGroups();
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        return groups.stream().mapToInt(StudentGroup::getStudentCount).sum();
    }

    private static boolean sharesAnyStudentGroup(Lesson a, Lesson b) {
        List<StudentGroup> groups = a.getStudentGroups();
        if (groups == null || groups.isEmpty()) {
            return false;
        }
        List<StudentGroup> otherGroups = b.getStudentGroups();
        if (otherGroups == null || otherGroups.isEmpty()) {
            return false;
        }
        for (StudentGroup g : groups) {
            if (otherGroups.contains(g)) {
                return true;
            }
        }
        return false;
    }
}
