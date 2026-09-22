package com.xrq.xxq.module.coursework.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentAnswer;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentAnswerMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentSubmissionMapper;
import com.xrq.xxq.module.coursework.material.entity.CourseMaterial;
import com.xrq.xxq.module.coursework.material.mapper.CourseMaterialMapper;
import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import com.xrq.xxq.module.coursework.video.mapper.CourseVideoMapper;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileUsageContributor;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * coursework 域文件用途登记：供 FileMaintenanceTask 孤儿成品回收对账，
 * 以及 FileUsageCompletenessTest 构建期守卫对账。
 * 逻辑删除行由 @TableLogic 自动排除 —— 「业务删记录 → 产物无引用 → 回收」的通道。
 * <p>
 * 认领的 4 个业务目录中，course_announcement 无文件列故不参与对账。
 */
@Component
@RequiredArgsConstructor
public class CourseworkFileUsageContributor implements FileUsageContributor {

    private static final Set<FileSource> SOURCES = Set.of(
            new FileSource("course_assignment", "file_name"),
            new FileSource("course_assignment_submission", "file_name"),
            new FileSource("course_assignment_answer", "answer_json"),
            new FileSource("course_video", "file_name"),
            new FileSource("course_material", "file_name"));

    private static final Set<FileBizEnum> BIZ = Set.of(
            FileBizEnum.COURSE_ASSIGNMENT,
            FileBizEnum.COURSE_ASSIGNMENT_SUBMISSION,
            FileBizEnum.COURSE_VIDEO,
            FileBizEnum.COURSE_MATERIAL);

    private final CourseAssignmentMapper assignmentMapper;
    private final CourseAssignmentSubmissionMapper submissionMapper;
    private final CourseAssignmentAnswerMapper answerMapper;
    private final CourseVideoMapper videoMapper;
    private final CourseMaterialMapper materialMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Set<FileBizEnum> biz() {
        return BIZ;
    }

    @Override
    public Set<FileSource> sources() {
        return SOURCES;
    }

    @Override
    public Set<String> referencedPaths(FileBizEnum biz) {
        return switch (biz) {
            case COURSE_ASSIGNMENT -> collect(assignmentMapper, CourseAssignment::getFileName);
            case COURSE_ASSIGNMENT_SUBMISSION -> {
                Set<String> paths = new HashSet<>(
                        collect(submissionMapper, CourseAssignmentSubmission::getFileName));
                paths.addAll(collectAnswerPaths());
                yield paths;
            }
            case COURSE_VIDEO -> collect(videoMapper, CourseVideo::getFileName);
            case COURSE_MATERIAL -> collect(materialMapper, CourseMaterial::getFileName);
            // 分发按 biz() 集合命中（FileMaintenanceTask.contributorOf），非本域 biz 不可达
            default -> Set.of();
        };
    }

    /** 答案 JSON 中的附件路径（files[].path，objects/ 前缀）。损坏 JSON 跳过。 */
    private Set<String> collectAnswerPaths() {
        List<CourseAssignmentAnswer> rows = answerMapper.selectList(
                new LambdaQueryWrapper<CourseAssignmentAnswer>()
                        .select(CourseAssignmentAnswer::getAnswerJson)
                        .isNotNull(CourseAssignmentAnswer::getAnswerJson)
                        .like(CourseAssignmentAnswer::getAnswerJson, "objects/%"));
        Set<String> paths = new HashSet<>();
        for (CourseAssignmentAnswer row : rows) {
            try {
                JsonNode files = objectMapper.readTree(row.getAnswerJson()).path("files");
                if (files.isArray()) {
                    for (JsonNode f : files) {
                        String path = f.path("path").asString(null);
                        if (path != null && !path.isBlank()) {
                            paths.add(path);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // 单行损坏不阻断全量对账
            }
        }
        return paths;
    }

    /** 只取目标列、跳过空值；返回该表现存的存储路径全集。 */
    private static <T> Set<String> collect(BaseMapper<T> mapper, SFunction<T, String> column) {
        return mapper.selectList(new LambdaQueryWrapper<T>()
                        .select(column)
                        .isNotNull(column)).stream()
                .map(column)
                .filter(path -> path != null && !path.isBlank())
                .collect(Collectors.toSet());
    }
}
