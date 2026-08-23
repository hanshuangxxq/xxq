package com.xrq.xxq.module.selection.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.dto.StudentCampaignResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCampaignTimeRestriction;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignTimeRestrictionMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionRecordService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.util.DistributedLock;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionRecordServiceImpl implements SelectionRecordService {

    private static final String COUNT_KEY_PREFIX = "selection:count:";
    private static final String INCR_LUA =
            "local cur = redis.call('INCR', KEYS[1]) " +
            "if cur > tonumber(ARGV[1]) then " +
            "  redis.call('DECR', KEYS[1]) " +
            "  return -1 " +
            "end " +
            "return cur";

    private final SelectionRecordMapper selectionRecordMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;
    private final SelectionGroupMapper selectionGroupMapper;
    private final SelectionCampaignTimeRestrictionMapper selectionCampaignTimeRestrictionMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final SemesterService semesterService;
    private final StringRedisTemplate redisTemplate;
    private final DistributedLock distributedLock;

    /**
     * 启动时校正 Redis 选课计数器与 DB 一致。
     * 防止 Redis 崩溃丢数据或应用异常导致计数器与 selection_record 表不一致（脏计数引发超卖或误拒）。
     */
    @PostConstruct
    public void reconcileCounters() {
        Set<String> keys = redisTemplate.keys(COUNT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            try {
                Long campaignId = Long.valueOf(key.substring(COUNT_KEY_PREFIX.length()));
                Long dbCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
                if (dbCount != null) {
                    redisTemplate.opsForValue().set(key, String.valueOf(dbCount));
                }
            } catch (NumberFormatException ignore) {
                // 非数字 campaignId 的 key，跳过
            }
        }
    }

    @Override
    @Transactional
    public SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(request.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            throw new BusinessException(409, "不在选课时间窗口内");
        }

        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, studentUserId));
        if (student == null) {
            throw new BusinessException(403, "学生信息不存在");
        }
        if (!isAllowed(campaign, student.getGradeId(), student.getMajorId())) {
            throw new BusinessException(409, "本课程不对您的年级或专业开放");
        }

        // 组内选课上限跨活动共用：统计该生在本组下、跨所有活动的已选课程数。
        if (campaign.getGroupId() != null) {
            SelectionGroup group = selectionGroupMapper.selectById(campaign.getGroupId());
            if (group == null) {
                throw new BusinessException(409, "该活动未归属任何选课组");
            }
            List<Long> siblingCampaignIds = selectionCampaignMapper.selectList(
                    new LambdaQueryWrapper<SelectionCampaign>()
                            .eq(SelectionCampaign::getGroupId, campaign.getGroupId()))
                    .stream().map(SelectionCampaign::getId).toList();
            Long selectedInGroup = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                    .eq(SelectionRecord::getStudentId, studentUserId)
                    .in(SelectionRecord::getCampaignId, siblingCampaignIds)
                    .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
            if (selectedInGroup >= group.getMaxCourses()) {
                throw new BusinessException(409, "超过本组选课上限 " + group.getMaxCourses() + " 门");
            }
        }

        // 分布式锁保护「查重 + 容量 INCR + 插入」关键段，关闭并发下同一生重复选课的竞态窗口。
        // 锁按 campaignId+studentId 粒度：同一学生重复提交被串行化；容量由 Redis Lua 原子计数器跨学生防超卖。
        return distributedLock.withLock(
                "sel:" + request.getCampaignId() + ":" + studentUserId, 30, () -> {
                    Long dupCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                            .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                            .eq(SelectionRecord::getStudentId, studentUserId)
                            .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
                    if (dupCount > 0) {
                        throw new BusinessException(409, "已选该课程");
                    }

                    String countKey = COUNT_KEY_PREFIX + request.getCampaignId();
                    DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_LUA, Long.class);
                    Long result = redisTemplate.execute(script, List.of(countKey), String.valueOf(campaign.getCapacity()));
                    if (result == null || result == -1) {
                        throw new BusinessException(409, "课程已满");
                    }

                    try {
                        SelectionRecord record = new SelectionRecord();
                        record.setCampaignId(request.getCampaignId());
                        record.setStudentId(studentUserId);
                        record.setStatus(RecordStatusEnum.SELECTED);
                        record.setSelectTime(LocalDateTime.now());
                        selectionRecordMapper.insert(record);
                        return toResponse(record, campaign);
                    } catch (Exception e) {
                        redisTemplate.opsForValue().decrement(countKey);
                        throw e;
                    }
                });
    }

    @Override
    @Transactional
    public void drop(Long studentUserId, Long recordId) {
        SelectionRecord record = selectionRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(404, "选课记录不存在");
        }
        if (!record.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "无权操作他人选课记录");
        }
        if (record.getStatus() != RecordStatusEnum.SELECTED) {
            throw new BusinessException(409, "该记录已退选");
        }
        SelectionCampaign campaign = selectionCampaignMapper.selectById(record.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放，不可退选");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            throw new BusinessException(409, "不在选课时间窗口内");
        }

        selectionRecordMapper.deleteById(recordId);

        String countKey = COUNT_KEY_PREFIX + record.getCampaignId();
        redisTemplate.opsForValue().decrement(countKey);
    }

    @Override
    public List<SelectionRecordResponse> listMy(Long studentUserId, Long campaignId) {
        List<SelectionRecord> records = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getStudentId, studentUserId)
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .orderByDesc(SelectionRecord::getSelectTime));
        if (records.isEmpty()) {
            return List.of();
        }
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        return records.stream()
                .map(r -> toResponse(r, campaign))
                .toList();
    }

    @Override
    public List<StudentCampaignResponse> listOpenCampaignsForStudent(Long studentUserId) {
        List<SelectionCampaign> campaigns = selectionCampaignMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaign>()
                        .eq(SelectionCampaign::getStatus, CampaignStatusEnum.OPEN)
                        .orderByAsc(SelectionCampaign::getId));
        if (campaigns.isEmpty()) {
            return List.of();
        }
        StudentCampaignContext ctx = loadStudentCampaignContext(campaigns, studentUserId);
        return campaigns.stream()
                .map(c -> toStudentResponse(c, ctx))
                .toList();
    }

    @Override
    public StudentCampaignResponse getCampaignForStudent(Long campaignId, Long studentUserId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        return toStudentResponse(campaign, loadStudentCampaignContext(List.of(campaign), studentUserId));
    }

    /**
     * 学生视角活动响应的批量预加载上下文：学期名/时段限制/选课组/同组活动/已选计数/我的已选集合，
     * 一次性批查后在内存组装，替代逐活动循环查库（N+1）。
     */
    private record StudentCampaignContext(
            Map<Long, String> semesterNames,
            Map<Long, List<Long>> timeRestrictionIdsByCampaign,
            Map<Long, SelectionGroup> groups,
            Map<Long, List<Long>> siblingIdsByGroup,
            Map<Long, Long> selectedCountByCampaign,
            Set<Long> mySelectedCampaignIds) {
    }

    private StudentCampaignContext loadStudentCampaignContext(List<SelectionCampaign> campaigns, Long studentUserId) {
        List<Long> campaignIds = campaigns.stream().map(SelectionCampaign::getId).toList();
        Map<Long, String> semesterNames = semesterService.toNameMap(
                campaigns.stream().map(SelectionCampaign::getSemesterId)
                        .filter(Objects::nonNull).distinct().toList());
        Map<Long, List<Long>> trByCampaign = selectionCampaignTimeRestrictionMapper.selectList(
                        new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                                .in(SelectionCampaignTimeRestriction::getCampaignId, campaignIds))
                .stream().collect(Collectors.groupingBy(SelectionCampaignTimeRestriction::getCampaignId,
                        Collectors.mapping(SelectionCampaignTimeRestriction::getTimeRestrictionId,
                                Collectors.toList())));
        List<Long> groupIds = campaigns.stream().map(SelectionCampaign::getGroupId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SelectionGroup> groups = groupIds.isEmpty()
                ? Map.of()
                : selectionGroupMapper.selectByIds(groupIds).stream()
                        .collect(Collectors.toMap(SelectionGroup::getId, g -> g, (a, b) -> a));
        Map<Long, List<Long>> siblingIdsByGroup = groupIds.isEmpty()
                ? Map.of()
                : selectionCampaignMapper.selectList(new LambdaQueryWrapper<SelectionCampaign>()
                                .in(SelectionCampaign::getGroupId, groupIds))
                        .stream().collect(Collectors.groupingBy(SelectionCampaign::getGroupId,
                                Collectors.mapping(SelectionCampaign::getId, Collectors.toList())));
        // 一次窄列查询同时算出「每活动已选人数」与「当前学生已选集合」（范围：列表活动 ∪ 同组活动）
        Set<Long> relevantIds = new HashSet<>(campaignIds);
        siblingIdsByGroup.values().forEach(relevantIds::addAll);
        List<SelectionRecord> selectedRecords = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .select(SelectionRecord::getId, SelectionRecord::getCampaignId,
                                SelectionRecord::getStudentId)
                        .in(SelectionRecord::getCampaignId, relevantIds)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        Map<Long, Long> selectedCountByCampaign = selectedRecords.stream()
                .collect(Collectors.groupingBy(SelectionRecord::getCampaignId, Collectors.counting()));
        Set<Long> mySelectedCampaignIds = selectedRecords.stream()
                .filter(r -> studentUserId.equals(r.getStudentId()))
                .map(SelectionRecord::getCampaignId)
                .collect(Collectors.toSet());
        return new StudentCampaignContext(semesterNames, trByCampaign, groups, siblingIdsByGroup,
                selectedCountByCampaign, mySelectedCampaignIds);
    }

    private StudentCampaignResponse toStudentResponse(SelectionCampaign campaign, StudentCampaignContext ctx) {
        StudentCampaignResponse resp = new StudentCampaignResponse();
        resp.setId(campaign.getId());
        resp.setName(campaign.getCourseName());
        resp.setSemesterId(campaign.getSemesterId());
        resp.setSemesterName(campaign.getSemesterId() == null
                ? null : ctx.semesterNames().get(campaign.getSemesterId()));
        resp.setStartWeek(campaign.getStartWeek());
        resp.setEndWeek(campaign.getEndWeek());
        resp.setStartTime(campaign.getStartTime());
        resp.setEndTime(campaign.getEndTime());
        resp.setStatus(campaign.getStatus());

        resp.setCourseId(null); // 公选课不再关联 course 表
        resp.setCourseCode(campaign.getCourseCode());
        resp.setCredit(campaign.getCredit());
        resp.setCourseHour(campaign.getCourseHour());
        resp.setDescription(campaign.getDescription());
        resp.setCourseType(campaign.getCourseType() != null ? campaign.getCourseType().getDescription() : null);
        resp.setAllowedGradeIds(parseLongs(campaign.getAllowedGradeIds()));
        resp.setAllowedMajors(parseLongs(campaign.getAllowedMajors()));
        resp.setTimeRestrictionIds(ctx.timeRestrictionIdsByCampaign()
                .getOrDefault(campaign.getId(), List.of()));
        resp.setCapacity(campaign.getCapacity());

        // 组上下文
        if (campaign.getGroupId() != null) {
            SelectionGroup group = ctx.groups().get(campaign.getGroupId());
            if (group != null) {
                resp.setGroupId(group.getId());
                resp.setGroupName(group.getName());
                resp.setGroupMax(group.getMaxCourses());
                long selectedInGroup = ctx.siblingIdsByGroup()
                        .getOrDefault(campaign.getGroupId(), List.of()).stream()
                        .filter(ctx.mySelectedCampaignIds()::contains)
                        .count();
                resp.setSelectedInGroup((int) selectedInGroup);
            }
        } else {
            resp.setSelectedInGroup(0);
        }

        // 选课统计（预加载聚合结果）
        int selectedCount = ctx.selectedCountByCampaign().getOrDefault(campaign.getId(), 0L).intValue();
        resp.setSelectedCount(selectedCount);
        resp.setRemaining(Math.max(0, campaign.getCapacity() - selectedCount));
        resp.setSelectedByMe(ctx.mySelectedCampaignIds().contains(campaign.getId()));

        return resp;
    }

    private SelectionRecordResponse toResponse(SelectionRecord record, SelectionCampaign campaign) {
        SelectionRecordResponse resp = new SelectionRecordResponse();
        resp.setId(record.getId());
        resp.setCampaignId(record.getCampaignId());
        resp.setStatus(record.getStatus());
        resp.setSelectTime(record.getSelectTime());
        resp.setDropTime(record.getDropTime());
        if (campaign != null) {
            resp.setCourseName(campaign.getCourseName());
            resp.setCourseCode(campaign.getCourseCode());
            resp.setCredit(campaign.getCredit());
            resp.setCourseType(campaign.getCourseType() != null ? campaign.getCourseType().getDescription() : null);
        }
        return resp;
    }

    private boolean isAllowed(SelectionCampaign campaign, Long studentGradeId, Long studentMajorId) {
        List<Long> allowedGrades = parseLongs(campaign.getAllowedGradeIds());
        if (!allowedGrades.isEmpty()) {
            if (studentGradeId == null || !allowedGrades.contains(studentGradeId)) {
                return false;
            }
        }
        List<Long> allowedMajors = parseLongs(campaign.getAllowedMajors());
        if (!allowedMajors.isEmpty()) {
            if (studentMajorId == null || !allowedMajors.contains(studentMajorId)) {
                return false;
            }
        }
        return true;
    }

    private List<Long> parseLongs(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
