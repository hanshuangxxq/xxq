package com.xrq.xxq.module.practice.graduation.controller;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.graduation.dto.AllocationRequest;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentResponse;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignResponse;
import com.xrq.xxq.module.practice.graduation.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.practice.graduation.dto.GraduationExportRow;
import com.xrq.xxq.module.practice.graduation.dto.PickRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalDeclareRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalResponse;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewRequest;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.service.GraduationService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * 毕业选题活动接口。
 * <p>
 * 教务：开启/管理活动、最终审查、导出；学生：自拟选题；院系：初审、统一分配；教师：自选学生。
 */
@RestController
@RequestMapping("/api/practice/graduation")
@RequiredArgsConstructor
public class GraduationController {

    private final GraduationService graduationService;
    private final AuthFacade authFacade;

    // ==================== 教务 ====================

    /** 新建选题活动（教务）。 */
    @PostMapping("/campaigns")
    public Result<CampaignResponse> createCampaign(HttpServletRequest request,
                                                   @RequestBody CampaignCreateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(graduationService.createCampaign(body));
    }

    /** 更新活动（教务）。 */
    @PutMapping("/campaigns/{id}")
    public Result<CampaignResponse> updateCampaign(HttpServletRequest request, @PathVariable Long id,
                                                   @RequestBody CampaignUpdateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(graduationService.updateCampaign(id, body));
    }

    /** 开放/关闭活动（教务）。 */
    @PutMapping("/campaigns/{id}/status")
    public Result<Void> changeCampaignStatus(HttpServletRequest request, @PathVariable Long id,
                                             @RequestParam CampaignStatusEnum status) {
        authFacade.requireAcademicAdmin(request);
        graduationService.changeCampaignStatus(id, status);
        return Result.ok();
    }

    /** 活动列表（教务）。 */
    @GetMapping("/campaigns")
    public Result<PageResult<CampaignResponse>> listCampaigns(HttpServletRequest request,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer pageSize) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(graduationService.listCampaigns(new PageQuery(page, pageSize)));
    }

    /** 活动详情（教务/院系/教师/学生可查活动信息）。 */
    @GetMapping("/campaigns/{id}")
    public Result<CampaignResponse> getCampaign(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN,
                AuthFacade.USER_TYPE_DEPARTMENT,
                AuthFacade.USER_TYPE_TEACHER,
                AuthFacade.USER_TYPE_STUDENT);
        return Result.ok(graduationService.getCampaign(id));
    }

    /** 教务最终审查匹配记录。 */
    @PostMapping("/assignments/{id}/review")
    public Result<AssignmentResponse> reviewAssignment(HttpServletRequest request, @PathVariable Long id,
                                                       @RequestBody AssignmentReviewRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(graduationService.reviewAssignment(id, body));
    }

    /** 导出活动全部申报（含匹配信息）为 xlsx，供送查重。 */
    @SneakyThrows
    @GetMapping("/campaigns/{id}/export")
    public ResponseEntity<byte[]> exportAssignments(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        List<GraduationExportRow> rows = graduationService.exportAssignments(id);
        byte[] bytes = buildExcel(rows);
        String filename = URLEncoder.encode("毕业选题导出_" + id + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ==================== 学生 ====================

    /** 学生自拟选题。 */
    @PostMapping("/proposals")
    public Result<ProposalResponse> declareProposal(HttpServletRequest request,
                                                    @RequestBody ProposalDeclareRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(graduationService.declareProposal(studentUserId, body));
    }

    /** 学生撤销申报（仅待审核）。 */
    @DeleteMapping("/proposals/{id}")
    public Result<Void> cancelProposal(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        graduationService.cancelProposal(studentUserId, id);
        return Result.ok();
    }

    /** 学生查看我的申报。 */
    @GetMapping("/proposals/my")
    public Result<List<ProposalResponse>> myProposals(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(graduationService.listMyProposals(studentUserId));
    }

    /** 学生查看自己在某活动的匹配结果。 */
    @GetMapping("/assignments/my")
    public Result<AssignmentResponse> myAssignment(HttpServletRequest request, @RequestParam Long campaignId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(graduationService.getMyAssignment(studentUserId, campaignId));
    }

    // ==================== 院系管理者 ====================

    /** 院系初审（仅本学院）。 */
    @PostMapping("/proposals/{id}/review")
    public Result<ProposalResponse> reviewProposal(HttpServletRequest request, @PathVariable Long id,
                                                   @RequestBody ProposalReviewRequest body) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(graduationService.reviewProposal(deptUserId, id, body));
    }

    /** 院系统一分配。 */
    @PostMapping("/allocations")
    public Result<AssignmentResponse> allocateStudent(HttpServletRequest request,
                                                      @RequestBody AllocationRequest body) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(graduationService.allocateStudent(deptUserId, body));
    }

    /** 本学院匹配池。 */
    @GetMapping("/dept/pool")
    public Result<List<ProposalResponse>> deptPool(HttpServletRequest request, @RequestParam Long campaignId) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(graduationService.listDeptPool(deptUserId, campaignId));
    }

    /** 本学院匹配记录。 */
    @GetMapping("/dept/assignments")
    public Result<List<AssignmentResponse>> deptAssignments(HttpServletRequest request, @RequestParam Long campaignId) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(graduationService.listDeptAssignments(deptUserId, campaignId));
    }

    // ==================== 教师 ====================

    /** 教师自选学生。 */
    @PostMapping("/picks")
    public Result<AssignmentResponse> pickStudent(HttpServletRequest request, @RequestBody PickRequest body) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(graduationService.pickStudent(teacherUserId, body));
    }

    /** 教师撤销自选。 */
    @DeleteMapping("/picks/{id}")
    public Result<Void> cancelPick(HttpServletRequest request, @PathVariable Long id) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        graduationService.cancelPick(teacherUserId, id);
        return Result.ok();
    }

    /** 教师本人匹配记录。 */
    @GetMapping("/teacher/assignments")
    public Result<List<AssignmentResponse>> teacherAssignments(HttpServletRequest request,
                                                               @RequestParam(required = false) Long campaignId) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(graduationService.listTeacherAssignments(teacherUserId, campaignId));
    }

    /** 教师可自选的本学院匹配池。 */
    @GetMapping("/teacher/pickable")
    public Result<List<ProposalResponse>> pickableProposals(HttpServletRequest request, @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(graduationService.listPickableProposals(teacherUserId, campaignId));
    }

    // ==================== 导出工具 ====================

    @SneakyThrows
    private byte[] buildExcel(List<GraduationExportRow> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("毕业选题");
            String[] headers = {"学号", "学生姓名", "学院", "选题标题", "教师工号", "教师姓名", "匹配来源", "匹配状态"};
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (int i = 0; i < rows.size(); i++) {
                GraduationExportRow r = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(nullToEmpty(r.getStudentNo()));
                row.createCell(1).setCellValue(nullToEmpty(r.getStudentName()));
                row.createCell(2).setCellValue(nullToEmpty(r.getCollegeName()));
                row.createCell(3).setCellValue(nullToEmpty(r.getProposalTitle()));
                row.createCell(4).setCellValue(nullToEmpty(r.getTeacherNo()));
                row.createCell(5).setCellValue(nullToEmpty(r.getTeacherName()));
                row.createCell(6).setCellValue(nullToEmpty(r.getSource()));
                row.createCell(7).setCellValue(nullToEmpty(r.getStatus()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
