package com.xrq.xxq.module.practice.graduation.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.DashboardRow;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationDashboardMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationDashboardService;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationDashboardServiceImpl implements GraduationDashboardService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] HEADERS = {"学号", "姓名", "院系", "班级", "年级", "选题状态",
            "题目名称", "指导教师", "匹配来源", "中期结论", "申请提交时间", "审批完成时间"};

    private final GraduationCampaignMapper campaignMapper;
    private final GraduationDashboardMapper dashboardMapper;
    private final GraduationLogService logService;
    private final StudentScopeResolver scopeResolver;

    @Override
    public PageResult<DashboardRow> listDashboard(Long campaignId, String status, String keyword,
                                                  Long collegeId, String userType, Long operatorUserId,
                                                  PageQuery pageQuery) {
        List<DashboardRow> all = loadAllRows(campaignId, status, keyword, collegeId, userType, operatorUserId);
        return PageResult.slice(all, pageQuery);
    }

    @Override
    public ExportFile exportDashboard(Long campaignId, String format, String status, String keyword,
                                      Long collegeId, String userType, Long operatorUserId,
                                      Long operatorId, String operatorType) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        List<DashboardRow> rows = loadAllRows(campaignId, status, keyword, collegeId, userType, operatorUserId);
        String fmt = format == null || format.isBlank() ? "xlsx" : format.toLowerCase();
        byte[] data;
        String suffix;
        if ("csv".equals(fmt)) {
            data = buildCsv(rows);
            suffix = ".csv";
        } else {
            data = buildXlsx(rows);
            suffix = ".xlsx";
        }
        // R-5.10：导出动作记录操作日志（导出人/时间/条数）
        logService.record(campaignId, operatorId, operatorType, "导出看板数据",
                "graduation_campaign", campaignId,
                "格式: " + fmt + ", 条数: " + rows.size()
                        + (status != null ? ", 状态筛选: " + status : "")
                        + (keyword != null ? ", 关键字: " + keyword : ""));
        return new ExportFile(data,
                "毕业设计看板-" + safeFileName(campaign.getName())
                        + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + suffix);
    }

    // ---- 看板数据 ----

    private List<DashboardRow> loadAllRows(Long campaignId, String status, String keyword,
                                           Long collegeId, String userType, Long operatorUserId) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        // R-10.1：院系管理者强制限定本院系范围
        Long effectiveCollegeId = collegeId;
        if ("department".equals(userType)) {
            effectiveCollegeId = scopeResolver.deptCollegeId(operatorUserId);
        }
        return dashboardMapper.selectDashboard(campaign.getId(), gradeIdsOf(campaign),
                effectiveCollegeId, keyword, status);
    }

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private List<Long> gradeIdsOf(GraduationCampaign campaign) {
        Set<Long> ids = new HashSet<>();
        if (campaign.getAllowedGradeIds() != null) {
            for (String part : campaign.getAllowedGradeIds().split(",")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略非法段
                }
            }
        }
        return List.copyOf(ids);
    }

    // ---- Excel 导出（R-5.10 xlsx）----

    private byte[] buildXlsx(List<DashboardRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("看板");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (DashboardRow row : rows) {
                Row dataRow = sheet.createRow(r++);
                dataRow.createCell(0).setCellValue(nvl(row.getStudentNo()));
                dataRow.createCell(1).setCellValue(nvl(row.getStudentName()));
                dataRow.createCell(2).setCellValue(nvl(row.getCollegeName()));
                dataRow.createCell(3).setCellValue(nvl(row.getClassName()));
                dataRow.createCell(4).setCellValue(nvl(row.getGradeName()));
                dataRow.createCell(5).setCellValue(statusText(row));
                dataRow.createCell(6).setCellValue(nvl(row.getProposalTitle()));
                dataRow.createCell(7).setCellValue(nvl(row.getTeacherName()));
                dataRow.createCell(8).setCellValue(row.getAssignmentSource() != null
                        ? row.getAssignmentSource().getDescription() : "");
                dataRow.createCell(9).setCellValue(midtermText(row));
                dataRow.createCell(10).setCellValue(timeText(row.getProposalSubmitTime()));
                dataRow.createCell(11).setCellValue(timeText(row.getProposalApprovedTime()));
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "Excel 导出失败");
        }
    }

    // ---- CSV 导出（R-5.10 csv 为辅）----

    private byte[] buildCsv(List<DashboardRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM: Excel can open utf-8 csv without garbled text
        sb.append(String.join(",", HEADERS)).append("\r\n");
        for (DashboardRow row : rows) {
            sb.append(csv(nvl(row.getStudentNo()))).append(',')
                    .append(csv(nvl(row.getStudentName()))).append(',')
                    .append(csv(nvl(row.getCollegeName()))).append(',')
                    .append(csv(nvl(row.getClassName()))).append(',')
                    .append(csv(nvl(row.getGradeName()))).append(',')
                    .append(csv(statusText(row))).append(',')
                    .append(csv(nvl(row.getProposalTitle()))).append(',')
                    .append(csv(nvl(row.getTeacherName()))).append(',')
                    .append(csv(row.getAssignmentSource() != null
                            ? row.getAssignmentSource().getDescription() : "")).append(',')
                    .append(csv(midtermText(row))).append(',')
                    .append(csv(timeText(row.getProposalSubmitTime()))).append(',')
                    .append(csv(timeText(row.getProposalApprovedTime()))).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- helpers ----

    /** 中期结论展示（R-7.5 教务看板预警）：未评审为空 */
    private String midtermText(DashboardRow row) {
        if (row.getMidtermConclusion() == null) {
            return "";
        }
        return row.getMidtermConclusion().getDescription();
    }

    /** 看板状态展示：无申请显示「未开始选题」，否则用状态描述（R-5.4） */
    private String statusText(DashboardRow row) {
        if (row.getProposalStatus() == null) {
            return "未开始选题";
        }
        return row.getProposalStatus().getDescription();
    }

    private String timeText(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FMT);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\""
                : value;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String safeFileName(String name) {
        return name == null ? "活动" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
