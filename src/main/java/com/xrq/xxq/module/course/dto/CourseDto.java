package com.xrq.xxq.module.course.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程视图抽象基类。
 * <p>
 * 按课程性质（{@link com.xrq.xxq.module.course.entity.CurseEnum}）派生子类，查询接口返回抽象类型。
 * Jackson 按 {@code category} 字段多态序列化，前端按类型处理，避免为选课班做特殊转化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "category", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RequiredCourseDto.class, name = "REQUIRE"),
        @JsonSubTypes.Type(value = ElectiveCourseDto.class, name = "ELECTIVE"),
        @JsonSubTypes.Type(value = PublicCourseDto.class, name = "PUBLIC"),
        @JsonSubTypes.Type(value = PracticeCourseDto.class, name = "PRACTICE")
})
public abstract class CourseDto {
    // 授课安排 id（teach_info.id）：成绩录入/考试按此关联，课表视图亦返回以供前端定位
    private Long id;
    // 课程信息（脱敏：不含 courseId、courseCode）
    private String courseName;
    private Integer credit;
    private Integer courseHour;
    private String courseType;
    // 教师信息（脱敏：不含 teacherId、teacherNo、title）
    private String teacherName;
    private String department;
    // 班级信息
    private String className;
    private String college;
    // 上课时间
    private Integer dayOfWeek;
    private Integer startWeek;
    private Integer endWeek;
    private Long timeId;
    private LocalTime startPeriod; // 上课开始时间（来自 time 表）
    private LocalTime endPeriod;   // 上课结束时间（来自 time 表）
    // 上课地点（脱敏：不含 localId）
    private String building;
    private String classroom;
}
