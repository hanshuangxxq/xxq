package com.xrq.xxq.module.course.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.TimeRestriction;
import com.xrq.xxq.module.course.service.TimeRestrictionService;

import lombok.RequiredArgsConstructor;

/**
 * 时段限制管理接口（教务管理员操作）。
 */
@RestController
@RequestMapping("/api/time-restrictions")
@RequiredArgsConstructor
public class TimeRestrictionController {

    private final TimeRestrictionService timeRestrictionService;

    /** 查询全部时段限制 */
    @GetMapping
    public Result<List<TimeRestriction>> list() {
        return Result.ok(timeRestrictionService.list());
    }

    /** 查询单条限制 */
    @GetMapping("/{id}")
    public Result<TimeRestriction> getById(@PathVariable Long id) {
        TimeRestriction restriction = timeRestrictionService.getById(id);
        if (restriction == null) {
            return Result.fail(404, "时段限制不存在");
        }
        return Result.ok(restriction);
    }

    /** 新增时段限制 */
    @PostMapping
    public Result<TimeRestriction> create(@RequestBody TimeRestriction restriction) {
        timeRestrictionService.save(restriction);
        return Result.ok(restriction);
    }

    /** 修改时段限制 */
    @PutMapping("/{id}")
    public Result<TimeRestriction> update(@PathVariable Long id, @RequestBody TimeRestriction restriction) {
        restriction.setId(id);
        timeRestrictionService.updateById(restriction);
        return Result.ok(restriction);
    }

    /** 删除时段限制 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        timeRestrictionService.removeById(id);
        return Result.ok();
    }
}
