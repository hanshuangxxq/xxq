package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.graduation.dto.SelectionApplyRequest;
import com.xrq.xxq.module.practice.graduation.dto.SelectionResponse;
import com.xrq.xxq.module.practice.graduation.dto.SelectionReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.TopicCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.TopicResponse;
import com.xrq.xxq.module.practice.graduation.dto.TopicUpdateRequest;
import com.xrq.xxq.module.practice.graduation.entity.GraduationSelection;
import com.xrq.xxq.module.practice.graduation.entity.GraduationTopic;
import com.xrq.xxq.module.practice.graduation.entity.SelectionStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.TopicStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationSelectionMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationTopicMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationServiceImpl
        extends ServiceImpl<GraduationTopicMapper, GraduationTopic>
        implements GraduationService {

    private final GraduationSelectionMapper selectionMapper;
    private final UserMapper userMapper;
    private final SemesterService semesterService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TopicResponse createTopic(Long teacherUserId, TopicCreateRequest request) {
        ParamValidator.requireNonBlank(request.getTitle(), "选题标题");
        ParamValidator.requirePositive(request.getCapacity(), "选题容量");
        GraduationTopic topic = new GraduationTopic();
        topic.setSemesterId(semesterService.resolveOrDefault(request.getSemesterId()));
        topic.setTeacherId(teacherUserId);
        topic.setTitle(request.getTitle());
        topic.setDescription(request.getDescription());
        topic.setRequirements(request.getRequirements());
        topic.setCapacity(request.getCapacity());
        topic.setSelectedCount(0);
        topic.setStatus(TopicStatusEnum.DRAFT);
        save(topic);
        return toTopicResponse(topic, nameOf(teacherUserId));
    }

    @Override
    @Transactional
    public TopicResponse updateTopic(Long topicId, TopicUpdateRequest request, Long operatorUserId, String userType) {
        GraduationTopic topic = requireTopicOwned(topicId, operatorUserId, userType);
        if (request.getTitle() != null) {
            ParamValidator.requireNonBlank(request.getTitle(), "选题标题");
            topic.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            topic.setDescription(request.getDescription());
        }
        if (request.getRequirements() != null) {
            topic.setRequirements(request.getRequirements());
        }
        if (request.getCapacity() != null) {
            ParamValidator.requirePositive(request.getCapacity(), "选题容量");
            int selected = topic.getSelectedCount() == null ? 0 : topic.getSelectedCount();
            if (request.getCapacity() < selected) {
                throw new BusinessException(409, "容量不能小于已申请人数");
            }
            topic.setCapacity(request.getCapacity());
        }
        updateById(topic);
        return toTopicResponse(topic, nameOf(topic.getTeacherId()));
    }

    @Override
    @Transactional
    public void changeTopicStatus(Long topicId, TopicStatusEnum status, Long operatorUserId, String userType) {
        if (status == null) {
            throw new BusinessException(400, "状态不能为空");
        }
        GraduationTopic topic = requireTopicOwned(topicId, operatorUserId, userType);
        topic.setStatus(status);
        updateById(topic);
    }

    @Override
    public PageResult<TopicResponse> listTopics(Long operatorUserId, String userType,
                                                Long teacherId, TopicStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<GraduationTopic> wrapper = new LambdaQueryWrapper<GraduationTopic>()
                .orderByDesc(GraduationTopic::getId);
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            // 教师只看自己发布的选题
            wrapper.eq(GraduationTopic::getTeacherId, operatorUserId);
        } else if (teacherId != null) {
            wrapper.eq(GraduationTopic::getTeacherId, teacherId);
        }
        if (status != null) {
            wrapper.eq(GraduationTopic::getStatus, status);
        }
        Page<GraduationTopic> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<GraduationTopic> topics = page.getRecords();
        if (topics.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        Map<Long, String> nameMap = userMapper.toNameMap(
                topics.stream().map(GraduationTopic::getTeacherId).distinct().toList());
        List<TopicResponse> records = topics.stream()
                .map(t -> toTopicResponse(t, nameMap.get(t.getTeacherId())))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public TopicResponse getTopic(Long topicId) {
        GraduationTopic topic = baseMapper.selectById(topicId);
        if (topic == null) {
            throw new BusinessException(404, "选题不存在");
        }
        return toTopicResponse(topic, nameOf(topic.getTeacherId()));
    }

    @Override
    public List<TopicResponse> listAvailableTopics(Long studentUserId) {
        List<GraduationTopic> topics = baseMapper.selectList(new LambdaQueryWrapper<GraduationTopic>()
                .eq(GraduationTopic::getStatus, TopicStatusEnum.OPEN)
                .orderByDesc(GraduationTopic::getId));
        Map<Long, String> nameMap = userMapper.toNameMap(
                topics.stream().map(GraduationTopic::getTeacherId).distinct().toList());
        return topics.stream()
                .map(t -> toTopicResponse(t, nameMap.get(t.getTeacherId())))
                .toList();
    }

    @Override
    @Transactional
    public SelectionResponse applyTopic(Long studentUserId, SelectionApplyRequest request) {
        ParamValidator.requireNonNull(request.getTopicId(), "选题");
        GraduationTopic topic = baseMapper.selectById(request.getTopicId());
        if (topic == null) {
            throw new BusinessException(404, "选题不存在");
        }
        if (topic.getStatus() != TopicStatusEnum.OPEN) {
            throw new BusinessException(409, "选题未开放");
        }
        // 应用层查重：同一选题不可重复申请（待审核/已通过）
        Long dup = selectionMapper.selectCount(new LambdaQueryWrapper<GraduationSelection>()
                .eq(GraduationSelection::getTopicId, request.getTopicId())
                .eq(GraduationSelection::getStudentId, studentUserId)
                .in(GraduationSelection::getStatus, SelectionStatusEnum.PENDING, SelectionStatusEnum.APPROVED));
        if (dup > 0) {
            throw new BusinessException(409, "已申请该选题");
        }
        // 容量防超卖：条件更新 selected_count+1 where count<capacity
        int affected = baseMapper.update(null, new LambdaUpdateWrapper<GraduationTopic>()
                .eq(GraduationTopic::getId, request.getTopicId())
                .lt(GraduationTopic::getSelectedCount, topic.getCapacity())
                .setSql("selected_count = selected_count + 1"));
        if (affected == 0) {
            throw new BusinessException(409, "选题已满");
        }
        GraduationSelection selection = new GraduationSelection();
        selection.setTopicId(request.getTopicId());
        selection.setStudentId(studentUserId);
        selection.setTeacherId(topic.getTeacherId());
        selection.setStatus(SelectionStatusEnum.PENDING);
        selection.setApplyReason(request.getApplyReason());
        selection.setSelectTime(LocalDateTime.now());
        selectionMapper.insert(selection);
        return toSelectionResponse(selection, topic.getTitle(),
                nameOf(studentUserId), nameOf(topic.getTeacherId()));
    }

    @Override
    @Transactional
    public void cancelApplication(Long studentUserId, Long applicationId) {
        GraduationSelection selection = selectionMapper.selectById(applicationId);
        if (selection == null) {
            throw new BusinessException(404, "申请不存在");
        }
        if (!selection.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (selection.getStatus() != SelectionStatusEnum.PENDING) {
            throw new BusinessException(409, "已处理的申请不可撤销");
        }
        selectionMapper.deleteById(applicationId);
        baseMapper.update(null, new LambdaUpdateWrapper<GraduationTopic>()
                .eq(GraduationTopic::getId, selection.getTopicId())
                .setSql("selected_count = GREATEST(selected_count - 1, 0)"));
    }

    @Override
    @Transactional
    public SelectionResponse reviewApplication(Long applicationId, SelectionReviewRequest request,
                                               Long operatorUserId, String userType) {
        if (request.getApproved() == null) {
            throw new BusinessException(400, "审核结果不能为空");
        }
        GraduationSelection selection = selectionMapper.selectById(applicationId);
        if (selection == null) {
            throw new BusinessException(404, "申请不存在");
        }
        if (selection.getStatus() != SelectionStatusEnum.PENDING) {
            throw new BusinessException(409, "该申请已处理");
        }
        GraduationTopic topic = baseMapper.selectById(selection.getTopicId());
        if (topic == null) {
            throw new BusinessException(404, "选题不存在");
        }
        ensureOwnsTopic(topic, operatorUserId, userType);
        selection.setStatus(request.getApproved() ? SelectionStatusEnum.APPROVED : SelectionStatusEnum.REJECTED);
        selection.setReviewComment(request.getReviewComment());
        selection.setReviewTime(LocalDateTime.now());
        selectionMapper.updateById(selection);
        // 驳回释放名额
        if (!request.getApproved()) {
            baseMapper.update(null, new LambdaUpdateWrapper<GraduationTopic>()
                    .eq(GraduationTopic::getId, topic.getId())
                    .setSql("selected_count = GREATEST(selected_count - 1, 0)"));
        }
        String title = "毕业设计选题审核结果";
        String content = "您的选题《" + topic.getTitle() + "》申请"
                + (request.getApproved() ? "已通过" : "已被驳回") + "。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(selection.getStudentId(), title, content));
        return toSelectionResponse(selection, topic.getTitle(),
                nameOf(selection.getStudentId()), nameOf(topic.getTeacherId()));
    }

    @Override
    public List<SelectionResponse> listMyApplications(Long studentUserId) {
        List<GraduationSelection> selections = selectionMapper.selectList(
                new LambdaQueryWrapper<GraduationSelection>()
                        .eq(GraduationSelection::getStudentId, studentUserId)
                        .orderByDesc(GraduationSelection::getId));
        return toSelectionResponses(selections);
    }

    @Override
    public PageResult<SelectionResponse> listApplicationsByTopic(Long topicId, Long operatorUserId,
                                                                 String userType, PageQuery pageQuery) {
        GraduationTopic topic = baseMapper.selectById(topicId);
        if (topic == null) {
            throw new BusinessException(404, "选题不存在");
        }
        ensureOwnsTopic(topic, operatorUserId, userType);
        Page<GraduationSelection> page = selectionMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<GraduationSelection>()
                        .eq(GraduationSelection::getTopicId, topicId)
                        .orderByDesc(GraduationSelection::getId));
        return PageResult.of(page, toSelectionResponses(page.getRecords()));
    }

    @Override
    @Transactional
    public void deleteTopic(Long topicId, Long operatorUserId, String userType) {
        GraduationTopic topic = requireTopicOwned(topicId, operatorUserId, userType);
        Long active = selectionMapper.selectCount(new LambdaQueryWrapper<GraduationSelection>()
                .eq(GraduationSelection::getTopicId, topicId)
                .in(GraduationSelection::getStatus, SelectionStatusEnum.PENDING, SelectionStatusEnum.APPROVED));
        if (active > 0) {
            throw new BusinessException(409, "存在未完结的申请，不可删除");
        }
        removeById(topicId);
    }

    // ---- helpers ----

    private GraduationTopic requireTopicOwned(Long topicId, Long operatorUserId, String userType) {
        GraduationTopic topic = baseMapper.selectById(topicId);
        if (topic == null) {
            throw new BusinessException(404, "选题不存在");
        }
        ensureOwnsTopic(topic, operatorUserId, userType);
        return topic;
    }

    private void ensureOwnsTopic(GraduationTopic topic, Long operatorUserId, String userType) {
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)
                && !topic.getTeacherId().equals(operatorUserId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    private List<SelectionResponse> toSelectionResponses(List<GraduationSelection> selections) {
        if (selections.isEmpty()) {
            return List.of();
        }
        List<Long> topicIds = selections.stream().map(GraduationSelection::getTopicId).distinct().toList();
        Map<Long, String> titleMap = baseMapper.selectByIds(topicIds).stream()
                .collect(Collectors.toMap(GraduationTopic::getId, GraduationTopic::getTitle, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        selections.forEach(s -> {
            personIds.add(s.getStudentId());
            personIds.add(s.getTeacherId());
        });
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return selections.stream()
                .map(s -> toSelectionResponse(s, titleMap.get(s.getTopicId()),
                        nameMap.get(s.getStudentId()), nameMap.get(s.getTeacherId())))
                .toList();
    }

    private TopicResponse toTopicResponse(GraduationTopic topic, String teacherName) {
        TopicResponse resp = new TopicResponse();
        resp.setId(topic.getId());
        resp.setSemesterId(topic.getSemesterId());
        resp.setTeacherId(topic.getTeacherId());
        resp.setTeacherName(teacherName);
        resp.setTitle(topic.getTitle());
        resp.setDescription(topic.getDescription());
        resp.setRequirements(topic.getRequirements());
        resp.setCapacity(topic.getCapacity());
        resp.setSelectedCount(topic.getSelectedCount());
        resp.setStatus(topic.getStatus());
        resp.setCreateTime(topic.getCreateTime());
        return resp;
    }

    private SelectionResponse toSelectionResponse(GraduationSelection selection, String topicTitle,
                                                  String studentName, String teacherName) {
        SelectionResponse resp = new SelectionResponse();
        resp.setId(selection.getId());
        resp.setTopicId(selection.getTopicId());
        resp.setTopicTitle(topicTitle);
        resp.setStudentId(selection.getStudentId());
        resp.setStudentName(studentName);
        resp.setTeacherId(selection.getTeacherId());
        resp.setTeacherName(teacherName);
        resp.setStatus(selection.getStatus());
        resp.setApplyReason(selection.getApplyReason());
        resp.setSelectTime(selection.getSelectTime());
        resp.setReviewTime(selection.getReviewTime());
        resp.setReviewComment(selection.getReviewComment());
        return resp;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
