package com.xrq.xxq.module.user.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class BatchImportResponse {
    private int total;
    private int successCount;
    private int failCount;
    private List<ImportResultDetail> details = new ArrayList<>();

    @Data
    public static class ImportResultDetail {
        private int index;
        private String username;
        private boolean success;
        private String message;
    }
}
