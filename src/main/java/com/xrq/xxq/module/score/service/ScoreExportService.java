package com.xrq.xxq.module.score.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.score.dto.ScoreView;

/**
 * 成绩导出服务：Excel（Apache POI）与 PDF（OpenPDF，内置 CJK 中文字体 STSong-Light）。
 */
@Service
public class ScoreExportService {

    private static final String[] COLUMNS = {"学号", "姓名", "平时分", "期末成绩", "平时占比(%)", "总评", "等级"};

    /** 导出 Excel（.xlsx）。 */
    public byte[] exportExcel(List<ScoreView> grades, String sheetName) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(sheetName));

            CellStyle titleStyle = buildTitleStyle(wb);
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle dataStyle = buildDataStyle(wb);

            // 标题行：跨所有列合并居中显示
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(sheetName);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMNS.length - 1));

            // 表头行
            Row header = sheet.createRow(1);
            for (Integer i = 0; i < COLUMNS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(COLUMNS[i]);
                c.setCellStyle(headerStyle);
            }

            // 数据行
            Integer r = 2;
            for (ScoreView g : grades) {
                Row row = sheet.createRow(r++);
                textCell(row, 0, str(g.getStudentNo()), dataStyle);
                textCell(row, 1, str(g.getStudentName()), dataStyle);
                numCell(row, 2, num(g.getRegularScore()), dataStyle);
                numCell(row, 3, num(g.getFinalScore()), dataStyle);
                numCell(row, 4, num(g.getRegularRatio()), dataStyle);
                numCell(row, 5, num(g.getTotalScore()), dataStyle);
                textCell(row, 6, str(g.getScoreLevel()), dataStyle);
            }

            // 自适应列宽：POI 原生 autoSizeColumn 对中文估宽偏窄会截断表头，这里按 CJK 字符宽度手动计算
            autoSizeColumns(sheet, COLUMNS.length);
            // 冻结标题+表头，滚动时常驻可见
            sheet.createFreezePane(0, 2);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "导出 Excel 失败");
        }
    }

    private CellStyle buildTitleStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(style);
        return style;
    }

    private CellStyle buildDataStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void textCell(Row row, Integer col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void numCell(Row row, Integer col, Double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /**
     * 按表头与数据内容自适应列宽，中文/CJK 字符按 2 个字符宽度估算，规避
     * POI {@link Sheet#autoSizeColumn(int)} 对中文估宽偏窄导致表头被截断的问题。
     * 跨列合并的单元格（如标题行）不计入单列宽度。
     */
    private void autoSizeColumns(Sheet sheet, Integer colCount) {
        for (Integer col = 0; col < colCount; col++) {
            Integer maxDisplay = 0;
            for (Integer rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    continue;
                }
                Cell cell = row.getCell(col);
                if (cell == null || inMergedRegion(sheet, rowIdx, col)) {
                    continue;
                }
                String text = cellText(cell);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                Integer w = displayWidth(text);
                if (w > maxDisplay) {
                    maxDisplay = w;
                }
            }
            // POI 列宽单位 = 1/256 字符宽；+2 留出留白，上限 255 字符
            Integer width = Math.min((maxDisplay + 2) * 256, 255 * 256);
            sheet.setColumnWidth(col, width);
        }
    }

    private Boolean inMergedRegion(Sheet sheet, Integer rowIdx, Integer col) {
        for (Integer i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).isInRange(rowIdx, col)) {
                return true;
            }
        }
        return false;
    }

    private String cellText(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /** 估算字符串在 Excel 中的显示宽度：CJK/全角字符计 2，其余计 1。 */
    private Integer displayWidth(String s) {
        Integer width = 0;
        for (Integer i = 0; i < s.length(); i++) {
            width += isWideChar(s.charAt(i)) ? 2 : 1;
        }
        return width;
    }

    private Boolean isWideChar(Character c) {
        if (c >= 0x1100 && c <= 0x115F) return true;   // Hangul Jamo
        if (c >= 0x2E80 && c <= 0x303E) return true;   // CJK 部首/标点
        if (c >= 0x3040 && c <= 0x33BF) return true;   // 假名/CJK/全角符号
        if (c >= 0x3400 && c <= 0x4DBF) return true;   // CJK 扩展 A
        if (c >= 0x4E00 && c <= 0x9FFF) return true;   // CJK 统一汉字
        if (c >= 0xA000 && c <= 0xA4CF) return true;   // 彝文
        if (c >= 0xAC00 && c <= 0xD7A3) return true;   // 韩文音节
        if (c >= 0xF900 && c <= 0xFAFF) return true;   // CJK 兼容汉字
        if (c >= 0xFE30 && c <= 0xFE4F) return true;   // CJK 兼容形式
        if (c >= 0xFF00 && c <= 0xFF60) return true;   // 全角字符
        if (c >= 0xFFE0 && c <= 0xFFE6) return true;   // 全角符号
        if (c >= 0x20000 && c <= 0x2FFFD) return true; // CJK 扩展 B+
        return false;
    }

    /** 导出 PDF，使用 STSong-Light 渲染中文。 */
    public byte[] exportPdf(List<ScoreView> grades, String title) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titleFont = cjkFont(16f);
            Font cellFont = cjkFont(10f);

            Paragraph p = new Paragraph(title, titleFont);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(COLUMNS.length);
            table.setWidthPercentage(100);
            for (String c : COLUMNS) {
                table.addCell(cell(c, cellFont));
            }
            for (ScoreView g : grades) {
                table.addCell(cell(str(g.getStudentNo()), cellFont));
                table.addCell(cell(str(g.getStudentName()), cellFont));
                table.addCell(cell(str(g.getRegularScore()), cellFont));
                table.addCell(cell(str(g.getFinalScore()), cellFont));
                table.addCell(cell(g.getRegularRatio() != null ? String.valueOf(g.getRegularRatio()) : "", cellFont));
                table.addCell(cell(str(g.getTotalScore()), cellFont));
                table.addCell(cell(str(g.getScoreLevel()), cellFont));
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "导出 PDF 失败");
        }
    }

    private Font cjkFont(Float size) {
        try {
            BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(bf, size);
        } catch (Exception e) {
            return new Font(Font.NORMAL, size);
        }
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private Double num(Number n) {
        return n != null ? n.doubleValue() : 0.0;
    }

    private String safeSheetName(String name) {
        if (name == null || name.isBlank()) {
            return "成绩";
        }
        // Excel sheet 名禁止字符：\ / ? * [ ] :
        return name.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
    }
}
