package com.xrq.xxq.module.practice.graduation.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.OperationLogResponse;
import com.xrq.xxq.module.practice.graduation.entity.GraduationOperationLog;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 毕业设计操作日志（R-10.4）—— JSONL 文件存储实现（不落数据库）。
 * <p>
 * 每个活动一个日志文件 {@code campaign-{campaignId}.jsonl}，一行一条 JSON 记录，追加写入；
 * 存储目录由 {@code practice.operation-log-path} 配置。主键 id 由应用层全局序列分配，
 * 首次写入时扫描既有日志恢复最大值，排序语义与原 DB 自增主键一致（id 越大越新）。
 * <p>
 * 注意：文件追加不参与业务事务，业务回滚时已写入的日志行不会撤销（视为留痕尝试记录）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraduationLogServiceImpl implements GraduationLogService {

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Value("${practice.operation-log-path:uploads/practice/graduation-operation-logs}")
    private String logPath;

    /** 追加写锁：防止并发写入交错（日志量小，单锁足够）。 */
    private final ReentrantLock writeLock = new ReentrantLock();
    /** 全局 id 序列，-1 表示尚未从磁盘恢复。 */
    private final AtomicLong idSequence = new AtomicLong(-1);

    @Override
    public void record(Long campaignId, Long operatorId, String operatorType,
                       String action, String targetType, Long targetId, String detail) {
        GraduationOperationLog entry = new GraduationOperationLog();
        entry.setId(nextId());
        entry.setCampaignId(campaignId);
        entry.setOperatorId(operatorId);
        entry.setOperatorType(operatorType);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail);
        entry.setCreateTime(LocalDateTime.now());

        String line = objectMapper.writeValueAsString(entry);
        writeLock.lock();
        try {
            Path dir = Path.of(logPath);
            Files.createDirectories(dir);
            Files.writeString(campaignFile(campaignId), line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new BusinessException(500, "操作日志写入失败");
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PageResult<OperationLogResponse> listLogs(Long campaignId, PageQuery pageQuery) {
        List<GraduationOperationLog> all = readEntries(campaignId);
        all.sort(Comparator.comparing(GraduationOperationLog::getId).reversed());
        PageResult<GraduationOperationLog> sliced = PageResult.slice(all, pageQuery);
        List<OperationLogResponse> records = toResponses(sliced.getRecords());
        return new PageResult<>(records, sliced.getTotal(), sliced.getPage(), sliced.getPageSize(), sliced.getPages());
    }

    /** 活动对应日志文件；无活动归属的日志归入 campaign-none.jsonl。 */
    private Path campaignFile(Long campaignId) {
        return Path.of(logPath).resolve("campaign-" + (campaignId == null ? "none" : campaignId) + ".jsonl");
    }

    /** 读取日志条目：指定活动只读对应文件，否则合并全部活动文件。 */
    private List<GraduationOperationLog> readEntries(Long campaignId) {
        if (campaignId != null) {
            return parseLines(campaignFile(campaignId));
        }
        Path dir = Path.of(logPath);
        List<GraduationOperationLog> all = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return all;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".jsonl")).toList()) {
                all.addAll(parseLines(file));
            }
        } catch (IOException e) {
            throw new BusinessException(500, "操作日志读取失败");
        }
        return all;
    }

    /** 解析单个日志文件；文件不存在返回空列表，坏行跳过不阻断查询。 */
    private List<GraduationOperationLog> parseLines(Path file) {
        List<GraduationOperationLog> entries = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    entries.add(objectMapper.readValue(line, GraduationOperationLog.class));
                } catch (Exception e) {
                    log.warn("跳过无法解析的操作日志行: {} ({})", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BusinessException(500, "操作日志读取失败");
        }
        return entries;
    }

    /** 分配全局单调递增 id：首次使用时扫描既有日志恢复最大值。 */
    private Long nextId() {
        if (idSequence.get() < 0) {
            synchronized (idSequence) {
                if (idSequence.get() < 0) {
                    long max = readEntries(null).stream()
                            .map(GraduationOperationLog::getId)
                            .filter(Objects::nonNull)
                            .mapToLong(Long::longValue)
                            .max().orElse(0L);
                    idSequence.set(max);
                }
            }
        }
        return idSequence.incrementAndGet();
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
