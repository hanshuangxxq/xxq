package com.xrq.xxq.module.user.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 批量导入用户（学生和老师）类
 * 主要用于接受前端的请求参数
 */
@Data
public class BatchImportResponse {
    private Integer total;
    private Integer successCount = 0;
    private Integer failCount = 0;
    private List<ImportResultDetail> details = new ArrayList<>();

    /**
     * 导入结果详情（导入的返回结果）
     */
    @Data
    public static class ImportResultDetail {
        private Integer index;
        private String username;
        private Boolean success;
        private String message;
    }
}
