package com.xrq.xxq.module.course.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeekScheduleDto {
    private Integer weekNumber;
    private LocalDate mondayDate;
    private Map<String, List<CourseDto>> scheduleByDay;
}
