package com.xrq.xxq.module.frontlog.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单条前端日志（与前端 LogEntry 一一对应）。全部字段可空 —— 上报内容完全由
 * 客户端控制，后端只做截断与落盘，字段缺失不拒绝（诊断数据，宁可缺不可丢）。
 * api / breadcrumbs / client 用 Map 承接：结构演进（前端加字段）不需要后端跟着改。
 */
@Data
public class FrontendLogItem {

    /** info / warn / error（未知值按 info 落盘） */
    private String level;

    /** js-error / unhandled-rejection / vue-error / api-error / page-view / manual */
    private String type;

    private String message;

    private String stack;

    /** 发生时的前端路由 fullPath */
    private String page;

    /** api-error 的接口上下文：method/path/status/code/durationMs（不含请求体） */
    private Map<String, Object> api;

    /** 错误前的行为痕迹（route/api/click，仅 warn/error 携带） */
    private List<Map<String, Object>> breadcrumbs;

    /** 客户端环境：version/ua/locale/viewport */
    private Map<String, Object> client;

    /** 客户端上报的登录用户 id（免登录接口，值可伪造，仅作筛查线索） */
    private Long userId;

    private String userType;

    /** 客户端毫秒时间戳 */
    private Long occurTime;

    /** 前端去重聚合的重复次数 */
    private Integer count;
}
