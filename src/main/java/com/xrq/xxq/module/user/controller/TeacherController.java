package com.xrq.xxq.module.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.TeacherDto;
import com.xrq.xxq.module.user.service.TeacherService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /** 查询教师列表（含姓名）。 */
    @GetMapping
    public Result<PageResult<TeacherDto>> list(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pageSize) {
        return Result.ok(teacherService.listTeachers(new PageQuery(page, pageSize)));
    }
}
