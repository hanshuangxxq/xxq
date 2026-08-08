package com.xrq.xxq.module.local.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.local.entity.Local;
import com.xrq.xxq.module.local.entity.LocalTypeEnum;
import com.xrq.xxq.module.local.service.LocalService;

import lombok.RequiredArgsConstructor;

/**
 * 上课地点管理接口。
 */
@RestController
@RequestMapping("/api/locals")
@RequiredArgsConstructor
public class LocalController {

    private final LocalService localService;

    @GetMapping
    public Result<PageResult<Local>> list(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer pageSize) {
        PageQuery pageQuery = new PageQuery(page, pageSize);
        LambdaQueryWrapper<Local> wrapper = new LambdaQueryWrapper<>();
        LocalTypeEnum typeEnum = LocalTypeEnum.fromValue(type);
        if (typeEnum != null) {
            wrapper.eq(Local::getType, typeEnum);
        }
        Page<Local> result = localService.page(pageQuery.toPage(), wrapper);
        return Result.ok(PageResult.of(result, result.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<Local> getById(@PathVariable Long id) {
        Local local = localService.getById(id);
        if (local == null) {
            return Result.fail(404, "上课地点不存在");
        }
        return Result.ok(local);
    }

    @PostMapping
    public Result<Local> create(@RequestBody Local local) {
        localService.save(local);
        return Result.ok(local);
    }

    @PutMapping("/{id}")
    public Result<Local> update(@PathVariable Long id, @RequestBody Local local) {
        local.setId(id);
        localService.updateById(local);
        return Result.ok(local);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        localService.removeById(id);
        return Result.ok();
    }
}
