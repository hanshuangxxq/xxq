package com.xrq.xxq.module.scheduling.cache;

import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 授课草稿项 — TeachInfo + 课程名 + 教师名。
 * <p>
 * 存入 Redis 时携带冗余名称字段，排课消费时无需再查库即可直接组装 {@code Lesson}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftItem {

    private Long id;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private String className;
    private String college;
    private Long timeId;
    private Long localId;
    private Integer dayOfWeek;
    private Integer startWeek;
    private Integer endWeek;
    private Long semesterId;

    /** 从 TeachInfo 创建富化项。 */
    public static DraftItem from(TeachInfo ti, String courseName, String teacherName, String college) {
        DraftItem item = new DraftItem();
        item.setId(ti.getId());
        item.setCourseId(ti.getCourseId());
        item.setCourseName(courseName);
        item.setTeacherId(ti.getTeacherId());
        item.setTeacherName(teacherName);
        item.setClassName(ti.getClassName());
        item.setCollege(college);
        item.setTimeId(ti.getTimeId());
        item.setLocalId(ti.getLocalId());
        item.setDayOfWeek(ti.getDayOfWeek());
        item.setStartWeek(ti.getStartWeek());
        item.setEndWeek(ti.getEndWeek());
        item.setSemesterId(ti.getSemesterId());
        return item;
    }

    /** 还原为 TeachInfo（用于排课时入库）。 */
    public TeachInfo toTeachInfo() {
        TeachInfo ti = new TeachInfo();
        ti.setId(id);
        ti.setCourseId(courseId);
        ti.setTeacherId(teacherId);
        ti.setClassName(className);
        ti.setTimeId(timeId);
        ti.setLocalId(localId);
        ti.setDayOfWeek(dayOfWeek);
        ti.setStartWeek(startWeek);
        ti.setEndWeek(endWeek);
        ti.setSemesterId(semesterId);
        return ti;
    }
}
