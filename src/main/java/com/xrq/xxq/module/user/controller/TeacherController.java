package com.xrq.xxq.module.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.TeacherDto;
import com.xrq.xxq.module.user.service.TeacherService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /** 查询所有教师（含姓名），用于排课下拉选择。 */
    @GetMapping
    public Result<List<TeacherDto>> list() {
        return Result.ok(teacherService.listTeachers());
    }
}
