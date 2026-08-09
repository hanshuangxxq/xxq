package com.xrq.xxq.module.practice.graduation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.common.PracticeFileService;
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.GraduationSelection;
import com.xrq.xxq.module.practice.graduation.entity.SelectionStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.Thesis;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationSelectionMapper;
import com.xrq.xxq.module.practice.graduation.mapper.ThesisMapper;
import com.xrq.xxq.module.practice.graduation.service.ThesisService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ThesisServiceImpl
        extends ServiceImpl<ThesisMapper, Thesis>
        implements ThesisService {

    private final GraduationSelectionMapper selectionMapper;
    private final UserMapper userMapper;
    private final PracticeFileService fileService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ThesisResponse submit(Long studentUserId, ThesisSubmitRequest request, MultipartFile file) {
        ParamValidator.requireNonNull(request.getSelectionId(), "选题申请");
        ParamValidator.requireNonBlank(request.getTitle(), "论文标题");
        GraduationSelection selection = selectionMapper.selectById(request.getSelectionId());
        if (selection == null) {
            throw new BusinessException(404, "选题申请不存在");
        }
        if (!selection.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (selection.getStatus() != SelectionStatusEnum.APPROVED) {
            throw new BusinessException(409, "选题未通过，不可提交论文");
        }
        // 同一选题申请仅一份论文；教师退回(REVISION)后学生可重新提交（覆盖文件）
        Thesis existing = baseMapper.selectOne(new LambdaQueryWrapper<Thesis>()
                .eq(Thesis::getSelectionId, request.getSelectionId())
                .last("LIMIT 1"));
        if (existing != null) {
            if (!existing.getStudentId().equals(studentUserId)) {
                throw new BusinessException(403, "权限不足");
            }
            if (existing.getStatus() != ThesisStatusEnum.REVISION) {
                throw new BusinessException(409, "已提交过论文，如需修改请联系指导教师退回");
            }
            PracticeFileService.StoredFile stored = fileService.store(file);
            existing.setTitle(request.getTitle());
            existing.setAbstractText(request.getAbstractText());
            existing.setFileName(stored.storedName());
            existing.setFileOriginal(stored.originalName());
            existing.setSubmitTime(LocalDateTime.now());
            existing.setStatus(ThesisStatusEnum.SUBMITTED);
            existing.setReviewScore(null);
            existing.setReviewComment(null);
            existing.setReviewTime(null);
            updateById(existing);
            return toResponse(existing, nameOf(studentUserId), nameOf(existing.getTeacherId()));
        }
        PracticeFileService.StoredFile stored = fileService.store(file);
        Thesis thesis = new Thesis();
        thesis.setSelectionId(request.getSelectionId());
        thesis.setStudentId(studentUserId);
        thesis.setTeacherId(selection.getTeacherId());
        thesis.setTitle(request.getTitle());
        thesis.setAbstractText(request.getAbstractText());
        thesis.setFileName(stored.storedName());
        thesis.setFileOriginal(stored.originalName());
        thesis.setSubmitTime(LocalDateTime.now());
        thesis.setStatus(ThesisStatusEnum.SUBMITTED);
        save(thesis);
        return toResponse(thesis, nameOf(studentUserId), nameOf(selection.getTeacherId()));
    }

    @Override
    @Transactional
    public ThesisResponse review(Long thesisId, ThesisReviewRequest request, Long reviewerUserId, String userType) {
        if (request.getStatus() == null) {
            throw new BusinessException(400, "评审状态不能为空");
        }
        Thesis thesis = baseMapper.selectById(thesisId);
        if (thesis == null) {
            throw new BusinessException(404, "论文不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)
                && !thesis.getTeacherId().equals(reviewerUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (thesis.getStatus() != ThesisStatusEnum.SUBMITTED
                && thesis.getStatus() != ThesisStatusEnum.UNDER_REVIEW
                && thesis.getStatus() != ThesisStatusEnum.REVISION) {
            throw new BusinessException(409, "当前论文状态不可评审");
        }
        thesis.setStatus(request.getStatus());
        thesis.setReviewScore(request.getReviewScore());
        thesis.setReviewComment(request.getReviewComment());
        thesis.setReviewTime(LocalDateTime.now());
        updateById(thesis);
        String title = "毕业论文评审结果";
        String content = "您的论文《" + thesis.getTitle() + "》评审状态更新为：" + request.getStatus().getDescription() + "。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(thesis.getStudentId(), title, content));
        return toResponse(thesis, nameOf(thesis.getStudentId()), nameOf(thesis.getTeacherId()));
    }

    @Override
    public ThesisResponse getMyThesis(Long studentUserId) {
        Thesis thesis = baseMapper.selectOne(new LambdaQueryWrapper<Thesis>()
                .eq(Thesis::getStudentId, studentUserId)
                .orderByDesc(Thesis::getId)
                .last("LIMIT 1"));
        if (thesis == null) {
            return null;
        }
        return toResponse(thesis, nameOf(studentUserId), nameOf(thesis.getTeacherId()));
    }

    @Override
    public PageResult<ThesisResponse> listForHandler(Long handlerUserId, String userType,
                                                    ThesisStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<Thesis> wrapper = new LambdaQueryWrapper<Thesis>()
                .orderByDesc(Thesis::getId);
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            wrapper.eq(Thesis::getTeacherId, handlerUserId);
        }
        if (status != null) {
            wrapper.eq(Thesis::getStatus, status);
        }
        Page<Thesis> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<Thesis> theses = page.getRecords();
        if (theses.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        List<Long> personIds = new ArrayList<>();
        theses.forEach(t -> {
            personIds.add(t.getStudentId());
            personIds.add(t.getTeacherId());
        });
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        List<ThesisResponse> records = theses.stream()
                .map(t -> toResponse(t, nameMap.get(t.getStudentId()), nameMap.get(t.getTeacherId())))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public Thesis loadForDownload(Long thesisId, Long operatorUserId, String userType) {
        Thesis thesis = baseMapper.selectById(thesisId);
        if (thesis == null) {
            throw new BusinessException(404, "论文不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            boolean allowed = thesis.getStudentId().equals(operatorUserId)
                    || thesis.getTeacherId().equals(operatorUserId);
            if (!allowed) {
                throw new BusinessException(403, "权限不足");
            }
        }
        return thesis;
    }

    @Override
    @Transactional
    public void deleteThesis(Long thesisId, Long operatorUserId, String userType) {
        Thesis thesis = baseMapper.selectById(thesisId);
        if (thesis == null) {
            throw new BusinessException(404, "论文不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            boolean isAdvisor = thesis.getTeacherId() != null && thesis.getTeacherId().equals(operatorUserId);
            boolean isOwnerStudent = thesis.getStudentId() != null && thesis.getStudentId().equals(operatorUserId)
                    && thesis.getStatus() == ThesisStatusEnum.SUBMITTED;
            if (!isAdvisor && !isOwnerStudent) {
                throw new BusinessException(403, "权限不足");
            }
        }
        String fileName = thesis.getFileName();
        removeById(thesisId);
        fileService.delete(fileName);
    }

    private ThesisResponse toResponse(Thesis thesis, String studentName, String teacherName) {
        ThesisResponse resp = new ThesisResponse();
        resp.setId(thesis.getId());
        resp.setSelectionId(thesis.getSelectionId());
        resp.setStudentId(thesis.getStudentId());
        resp.setStudentName(studentName);
        resp.setTeacherId(thesis.getTeacherId());
        resp.setTeacherName(teacherName);
        resp.setTitle(thesis.getTitle());
        resp.setAbstractText(thesis.getAbstractText());
        resp.setFileOriginal(thesis.getFileOriginal());
        resp.setSubmitTime(thesis.getSubmitTime());
        resp.setStatus(thesis.getStatus());
        resp.setReviewScore(thesis.getReviewScore());
        resp.setReviewComment(thesis.getReviewComment());
        resp.setReviewTime(thesis.getReviewTime());
        return resp;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
