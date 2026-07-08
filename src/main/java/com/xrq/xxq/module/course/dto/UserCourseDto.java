package com.xrq.xxq.module.course.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCourseDto {
    private LocalDate mondayDate;
    private List<CourseDto> courses;
}
