package com.xrq.xxq.module.exam.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.exam.dto.ClassCourseOptionDto;
import com.xrq.xxq.module.exam.dto.ExamCreateRequest;
import com.xrq.xxq.module.exam.dto.ExamView;
import com.xrq.xxq.module.exam.dto.MakeupCandidateDto;
import com.xrq.xxq.module.exam.dto.MakeupExamCreateRequest;
import com.xrq.xxq.module.exam.entity.Exam;
import com.xrq.xxq.module.exam.entity.ExamTypeEnum;

/**
 * 考试服务：教务安排考试、教师/学生查询。
 */
public interface ExamService extends IService<Exam> {

    /** 教务创建考试（期末/期中）。 */
    ExamView create(ExamCreateRequest request, Long userId);

    /** 教务修改考试。 */
    ExamView update(Long id, ExamCreateRequest request, Long userId);

    /** 教务删除考试（连带清理考生名单）。 */
    void delete(Long id, Long userId);

    /** 教务查询考试列表（可按学期/课程/类型过滤；source=SELECTION_CAMPAIGN 时 courseId 按 campaignId 过滤）。 */
    PageResult<ExamView> list(Long semesterId, Long courseId, String source, ExamTypeEnum examType, PageQuery pageQuery);

    /** 教师查询自己课程相关的考试。 */
    List<ExamView> listForTeacher(Long userId);

    /** 学生查询自己的考试（常规考试含公选课 ∪ 补考/重修）。 */
    List<ExamView> listMyExams(Long studentUserId);

    // ──────────────────────── 补考/重修 ────────────────────────

    /** 查询不及格学生名单（自动生成补考候选；source=SELECTION_CAMPAIGN 时 courseId 按 campaignId 过滤）。 */
    List<MakeupCandidateDto> listMakeupCandidates(Long courseId, String source, Long semesterId);

    /** 建补考/重修考试，并按不及格名单自动生成考生。 */
    ExamView createMakeupExam(MakeupExamCreateRequest request, Long userId);

    /** 查询补考/重修考试列表。 */
    List<ExamView> listMakeupExams(Long semesterId);

    /** 教务按班级查询可排考的课程（合班自动命中，返回所有学期）。 */
    List<ClassCourseOptionDto> listClassCourseOptions(Long classId);
}
