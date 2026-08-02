package com.xrq.xxq.module.score.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                header.createCell(i).setCellValue(COLUMNS[i]);
            }
            int r = 1;
            for (ScoreView g : grades) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(g.getStudentNo()));
                row.createCell(1).setCellValue(str(g.getStudentName()));
                row.createCell(2).setCellValue(num(g.getRegularScore()));
                row.createCell(3).setCellValue(num(g.getFinalScore()));
                row.createCell(4).setCellValue(g.getRegularRatio() != null ? g.getRegularRatio() : 0);
                row.createCell(5).setCellValue(num(g.getTotalScore()));
                row.createCell(6).setCellValue(str(g.getScoreLevel()));
            }
            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "导出 Excel 失败");
        }
    }

    /** 导出 PDF，使用 STSong-Light 渲染中文。 */
    public byte[] exportPdf(List<ScoreView> grades, String title) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titleFont = cjkFont(16);
            Font cellFont = cjkFont(10);

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

    private Font cjkFont(float size) {
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

    private double num(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0;
    }

    private String safeSheetName(String name) {
        if (name == null || name.isBlank()) {
            return "成绩";
        }
        // Excel sheet 名禁止字符：\ / ? * [ ] :
        return name.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
    }
}
