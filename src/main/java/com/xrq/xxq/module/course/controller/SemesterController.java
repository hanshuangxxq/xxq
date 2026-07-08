package com.xrq.xxq.module.course.controller;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.Semester;
import com.xrq.xxq.module.course.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/semester")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @GetMapping
    public Result<List<Semester>> list() {
        return Result.ok(semesterService.list());
    }

    @GetMapping("/current")
    public Result<Semester> getCurrent() {
        Semester semester = semesterService.getCurrent();
        if (semester == null) {
            return Result.fail(404, "没有当前学期的数据");
        }
        return Result.ok(semester);
    }

    @PostMapping
    public Result<Semester> create(@RequestBody Semester semester) {
        resolveSemesterWeeks(semester);
        if ("CURRENT".equals(semester.getStatus())) {
            semesterService.list().stream()
                    .filter(s -> "CURRENT".equals(s.getStatus()))
                    .forEach(s -> {
                        s.setStatus("HISTORICAL");
                        semesterService.updateById(s);
                    });
        }
        semesterService.save(semester);
        return Result.ok(semester);
    }

    @PutMapping("/{id}")
    public Result<Semester> update(@PathVariable Long id, @RequestBody Semester semester) {
        semester.setId(id);
        resolveSemesterWeeks(semester);
        if ("CURRENT".equals(semester.getStatus())) {
            semesterService.list().stream()
                    .filter(s -> "CURRENT".equals(s.getStatus()) && !s.getId().equals(id))
                    .forEach(s -> {
                        s.setStatus("HISTORICAL");
                        semesterService.updateById(s);
                    });
        }
        semesterService.updateById(semester);
        return Result.ok(semester);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        semesterService.removeById(id);
        return Result.ok();
    }

    /**
     * 根据周数、开始日期、结束日期中任意两个自动计算第三个。
     * 三个都传时校验一致性，不匹配则返回错误提示。
     *
     * <pre>
     * 前端可自由组合：
     *   startWeek + endWeek + startDate  → 自动算出 endDate
     *   startWeek + endWeek + endDate    → 自动算出 startDate
     *   startDate  + endDate             → 自动算出 endWeek（startWeek 默认为 1）
     *   startWeek + endWeek + startDate + endDate → 校验是否匹配
     * </pre>
     */
    private void resolveSemesterWeeks(Semester s) {
        if (s.getStartWeek() == null) {
            s.setStartWeek(1);
        }

        boolean hasEndWeek = s.getEndWeek() != null;
        boolean hasStartDate = s.getStartDate() != null;
        boolean hasEndDate = s.getEndDate() != null;

        // 从日期推导周数
        if (!hasEndWeek && hasStartDate && hasEndDate) {
            long days = ChronoUnit.DAYS.between(s.getStartDate(), s.getEndDate());
            if (days < 0) {
                throw new BusinessException(400, "开始日期不能晚于结束日期");
            }
            int totalWeeks = (int) days / 7 + 1;
            s.setEndWeek(s.getStartWeek() + totalWeeks - 1);
            return;
        }

        if (!hasEndWeek) {
            throw new BusinessException(400, "结束周不能为空（可通过开始日期+结束日期自动推导）");
        }

        int totalWeeks = s.getEndWeek() - s.getStartWeek() + 1;
        if (totalWeeks <= 0) {
            throw new BusinessException(400, "结束周必须大于等于起始周");
        }

        int provided = (hasStartDate ? 1 : 0) + (hasEndDate ? 1 : 0);
        if (provided == 0) {
            return;
        }

        if (provided == 2) {
            long days = ChronoUnit.DAYS.between(s.getStartDate(), s.getEndDate());
            if (days < 0) {
                throw new BusinessException(400, "开始日期不能晚于结束日期");
            }
            int expectedWeeks = (int) days / 7 + 1;
            if (expectedWeeks != totalWeeks) {
                throw new BusinessException(400,
                        "日期范围与周数不匹配：开始日期(" + s.getStartDate()
                        + ") 到结束日期(" + s.getEndDate()
                        + ") 相差 " + days + " 天，对应 " + expectedWeeks + " 周，"
                        + "但给定的周数范围为 " + totalWeeks + " 周（"
                        + s.getStartWeek() + "-" + s.getEndWeek() + "周）");
            }
            return;
        }

        // 只有一个日期：自动计算另一个
        if (hasStartDate) {
            s.setEndDate(s.getStartDate().plusDays(totalWeeks * 7L - 1));
        } else {
            s.setStartDate(s.getEndDate().minusDays(totalWeeks * 7L - 1));
        }
    }
}
