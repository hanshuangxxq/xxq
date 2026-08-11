package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.OperationLogResponse;
import com.xrq.xxq.module.practice.graduation.entity.GraduationOperationLog;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationOperationLogMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationLogServiceImpl implements GraduationLogService {

    private final GraduationOperationLogMapper logMapper;
    private final UserMapper userMapper;

    @Override
    public void record(Long campaignId, Long operatorId, String operatorType,
                       String action, String targetType, Long targetId, String detail) {
        GraduationOperationLog log = new GraduationOperationLog();
        log.setCampaignId(campaignId);
        log.setOperatorId(operatorId);
        log.setOperatorType(operatorType);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);
    }

    @Override
    public PageResult<OperationLogResponse> listLogs(Long campaignId, PageQuery pageQuery) {
        Page<GraduationOperationLog> page = logMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<GraduationOperationLog>()
                        .eq(campaignId != null, GraduationOperationLog::getCampaignId, campaignId)
                        .orderByDesc(GraduationOperationLog::getId));
        List<OperationLogResponse> records = toResponses(page.getRecords());
        return PageResult.of(page, records);
    }

    private List<OperationLogResponse> toResponses(List<GraduationOperationLog> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        List<Long> operatorIds = logs.stream().map(GraduationOperationLog::getOperatorId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(operatorIds);
        return logs.stream().map(l -> {
            OperationLogResponse resp = new OperationLogResponse();
            resp.setId(l.getId());
            resp.setCampaignId(l.getCampaignId());
            resp.setOperatorId(l.getOperatorId());
            resp.setOperatorName(nameMap.get(l.getOperatorId()));
            resp.setOperatorType(l.getOperatorType());
            resp.setAction(l.getAction());
            resp.setTargetType(l.getTargetType());
            resp.setTargetId(l.getTargetId());
            resp.setDetail(l.getDetail());
            resp.setCreateTime(l.getCreateTime());
            return resp;
        }).toList();
    }
}
