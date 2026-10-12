package com.xrq.xxq.module.frontlog.dto;

import lombok.Data;

import java.util.List;

/** 前端日志批量上报请求。 */
@Data
public class FrontendLogReportRequest {

    private List<FrontendLogItem> logs;
}
