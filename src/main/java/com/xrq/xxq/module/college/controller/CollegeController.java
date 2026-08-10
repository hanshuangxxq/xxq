package com.xrq.xxq.module.college.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.college.dto.CollegeCreateRequest;
import com.xrq.xxq.module.college.dto.CollegeResponse;
import com.xrq.xxq.module.college.dto.CollegeUpdateRequest;
import com.xrq.xxq.module.college.service.CollegeService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 院系接口。
 * <p>
 * 增删改：教务管理员；查询：教务/院系/教师。
 */
@RestController
@RequestMapping("/api/colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeService collegeService;
    private final AuthFacade authFacade;

    /** 院系列表（教务/院系/教师可查）。 */
    @GetMapping
    public Result<List<CollegeResponse>> list(HttpServletRequest request) {
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN,
                AuthFacade.USER_TYPE_DEPARTMENT,
                AuthFacade.USER_TYPE_TEACHER);
        List<CollegeResponse> list = collegeService.list().stream()
                .map(c -> {
                    CollegeResponse resp = new CollegeResponse();
                    resp.setId(c.getId());
                    resp.setCollegeName(c.getCollegeName());
                    resp.setCollegeCode(c.getCollegeCode());
                    resp.setCollegeNo(c.getCollegeNo());
                    resp.setCreateTime(c.getCreateTime());
                    resp.setUpdateTime(c.getUpdateTime());
                    return resp;
                })
                .toList();
        return Result.ok(list);
    }

    /** 新建院系（教务）。 */
    @PostMapping
    public Result<CollegeResponse> create(HttpServletRequest request,
                                          @RequestBody CollegeCreateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(collegeService.create(body));
    }

    /** 更新院系（教务）。 */
    @PutMapping("/{id}")
    public Result<CollegeResponse> update(HttpServletRequest request, @PathVariable Long id,
                                          @RequestBody CollegeUpdateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(collegeService.update(id, body));
    }

    /** 删除院系（教务）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        collegeService.delete(id);
        return Result.ok();
    }
}
