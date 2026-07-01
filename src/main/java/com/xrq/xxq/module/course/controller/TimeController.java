package com.xrq.xxq.module.course.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.Time;
import com.xrq.xxq.module.course.service.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/time")
@RequiredArgsConstructor
public class TimeController {

    private final TimeService timeService;

    @GetMapping
    public Result<List<Time>> list() {
        return Result.ok(timeService.list());
    }

    @GetMapping("/{id}")
    public Result<Time> getById(@PathVariable Long id) {
        Time time = timeService.getById(id);
        if (time == null) {
            return Result.fail(404, "时间段不存在");
        }
        return Result.ok(time);
    }
}
