package com.xrq.xxq.module.scheduling.constraint;

import java.util.Set;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.xrq.xxq.module.scheduling.domain.Lesson;

/**
 * 排课约束定义。
 * <p>
 * 硬约束（不可违反）：
 * <ul>
 *   <li>教室冲突：同学期 + 同一时间同一教室 + 周次重叠 → 不能有两节课</li>
 *   <li>教师冲突：同学期 + 同一时间 + 周次重叠 → 一位教师不能上两节课</li>
 *   <li>班级冲突：同学期 + 同一时间 + 周次重叠 → 一个班级不能有两节课</li>
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
     * 硬约束：同学期 + 同一时间 + 同一教室 + 周次重叠 → 只能有一节课。
     * 不同学期的课程互不冲突，周次不重叠的课程可以共享同一时段和教室。
     */
    private Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeslot),
                        Joiners.equal(Lesson::getRoom))
                .filter((a, b) -> sameSemester(a, b) && weeksOverlap(a, b))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    /**
     * 硬约束：同学期 + 同一时间 + 周次重叠 → 同一教师只能上一节课。
     */
    private Constraint teacherConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeslot),
                        Joiners.equal(Lesson::getTeacherId))
                .filter((a, b) -> sameSemester(a, b) && weeksOverlap(a, b))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher conflict");
    }

    /**
     * 硬约束：同学期 + 同一时间 + 周次重叠 → 任意班级不能有两节课。
     * 使用交集判断支持合班/重修场景——只要两个课堂共享任意一个班级即构成冲突。
     */
    private Constraint classConflict(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getTimeslot),
                        Joiners.lessThan(Lesson::getId))
                .filter((l1, l2) -> sameSemester(l1, l2) && weeksOverlap(l1, l2) && sharesAnyStudent(l1, l2))
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
                .filter(lesson -> lesson.getStudentCount() > lesson.getRoom().getCapacity())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room capacity");
    }

    private static boolean sharesAnyStudent(Lesson a, Lesson b) {
        Set<Long> aIds = a.getStudentIds();
        if (aIds == null || aIds.isEmpty()) {
            return false;
        }
        Set<Long> bIds = b.getStudentIds();
        if (bIds == null || bIds.isEmpty()) {
            return false;
        }
        for (Long id : aIds) {
            if (bIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean weeksOverlap(Lesson a, Lesson b) {
        if (a.getStartWeek() == null || a.getEndWeek() == null
                || b.getStartWeek() == null || b.getEndWeek() == null) {
            return true;
        }
        return a.getStartWeek() <= b.getEndWeek() && b.getStartWeek() <= a.getEndWeek();
    }

    /** 同一学期或任一为 null（保守处理）时返回 true。不同学期返回 false，不产生冲突。 */
    private static boolean sameSemester(Lesson a, Lesson b) {
        if (a.getSemesterId() == null || b.getSemesterId() == null) {
            return true;
        }
        return a.getSemesterId().equals(b.getSemesterId());
    }
}
