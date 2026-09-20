package com.xrq.xxq.module.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.user.dto.UserImportItem;

/**
 * 用户批量导入 Excel 解析器：模板固定 7 列
 * （用户名/密码/用户类型/学号或工号/年级/性别/班级或院系，对应 {@link UserImportItem} 字段序）。
 * <p>
 * 纯静态、按列位解析而非表头名匹配——表头只做「是不是本模板」的锚点校验，
 * 列序由模板端点保证，避免用户改表头文案导致的静默错列。
 * 解析只做形状转换（单元格 → 字符串），业务校验（必填/查重/字典解析）
 * 全部留给 {@link BatchImportService#batchImport} 的逐行事务逻辑，错误口径与 JSON 导入一致。
 */
public final class BatchImportExcelParser {

    private static final int COLUMN_COUNT = 7;

    /** 表头锚点：首列必须是它，否则认为没用模板 */
    private static final String FIRST_HEADER = "用户名";

    private BatchImportExcelParser() {
    }

    /**
     * 解析 xlsx 为导入条目。
     *
     * @param fileName 原始文件名（只取扩展名做格式校验）
     * @param in       文件内容流（本方法内关闭 Workbook，不会消费外层流的其他用途）
     */
    public static List<UserImportItem> parse(String fileName, InputStream in) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(400, "仅支持 .xlsx 格式，请下载导入模板");
        }
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null || !FIRST_HEADER.equals(cellString(header.getCell(0)))) {
                throw new BusinessException(400, "表头不符合模板（首列应为「用户名」），请下载导入模板");
            }
            List<UserImportItem> items = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>(COLUMN_COUNT);
                for (int c = 0; c < COLUMN_COUNT; c++) {
                    cells.add(cellString(row.getCell(c)));
                }
                // 全空行跳过（Excel 常见尾部格式化空行）
                if (cells.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                UserImportItem item = new UserImportItem();
                item.setUsername(cells.get(0));
                item.setPassword(cells.get(1));
                item.setUserType(cells.get(2));
                item.setIdentifier(cells.get(3));
                item.setClassName(cells.get(4));
                item.setGender(cells.get(5));
                item.setDepartment(cells.get(6));
                items.add(item);
            }
            if (items.isEmpty()) {
                throw new BusinessException(400, "文件中没有数据行");
            }
            return items;
        } catch (IOException e) {
            throw new BusinessException(400, "Excel 解析失败: " + e.getMessage());
        }
    }

    /** 单元格转字符串：数值去小数尾巴（学号/工号常被 Excel 存成数值），空白归一为空串。 */
    private static String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> numeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? numeric(cell.getNumericCellValue()) : cell.getStringCellValue().strip();
            default -> "";
        };
    }

    private static String numeric(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            // 整数值按整数输出：BigDecimal.valueOf 会保留 ".0"（学号/工号常被 Excel 存成 2023001.0）
            return BigDecimal.valueOf(d).toBigInteger().toString();
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }
}
