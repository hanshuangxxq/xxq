package com.xrq.xxq.module.practice.graduation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.xrq.xxq.module.practice.graduation.dto.DashboardRow;

/**
 * 看板聚合查询（R-5.8/R-6.14）。
 * <p>
 * 以活动参与年级学生为底表，左连接选题申请与师生匹配，输出教务/院系看板所需全部字段。
 * 状态筛选特殊值：NOT_SUBMITTED 未开始选题 / PENDING 初审中（两态之和）/ 其余为 {@code ProposalStatusEnum} 单值。
 */
@Mapper
public interface GraduationDashboardMapper {

    @Select("""
            <script>
            SELECT
                s.user_id                AS studentId,
                s.student_no             AS studentNo,
                u.name                   AS studentName,
                c.class_name             AS className,
                c.college_id             AS collegeId,
                co.college_name          AS collegeName,
                s.grade_id               AS gradeId,
                g.name                   AS gradeName,
                p.id                     AS proposalId,
                p.title                  AS proposalTitle,
                p.content                AS proposalContent,
                p.status                 AS proposalStatus,
                p.submit_time            AS proposalSubmitTime,
                (SELECT MAX(r.review_time) FROM graduation_proposal_review r
                  WHERE r.proposal_id = p.id AND r.stage = 'ACADEMIC' AND r.action = 'APPROVE') AS proposalApprovedTime,
                a.id                     AS assignmentId,
                a.teacher_id             AS teacherId,
                a.source                 AS assignmentSource,
                t.name                   AS teacherName,
                m.id                     AS midtermId,
                m.conclusion             AS midtermConclusion
            FROM student s
            JOIN `user` u ON u.id = s.user_id
            LEFT JOIN class_name c ON c.id = s.class_id
            LEFT JOIN college co ON co.id = c.college_id
            LEFT JOIN grade g ON g.id = s.grade_id
            LEFT JOIN graduation_proposal p
                   ON p.campaign_id = #{campaignId} AND p.student_id = s.user_id AND p.deleted = 0
            LEFT JOIN graduation_assignment a
                   ON a.campaign_id = #{campaignId} AND a.student_id = s.user_id AND a.deleted = 0
            LEFT JOIN `user` t ON t.id = a.teacher_id
            LEFT JOIN graduation_midterm m
                   ON m.campaign_id = #{campaignId} AND m.student_id = s.user_id AND m.deleted = 0
            WHERE u.deleted = 0
              AND s.grade_id IN
              <foreach collection="gradeIds" item="gid" open="(" separator="," close=")">
                  #{gid}
              </foreach>
              <if test="collegeId != null">
                  AND c.college_id = #{collegeId}
              </if>
              <if test="keyword != null and keyword != ''">
                  AND (u.name LIKE CONCAT('%', #{keyword}, '%')
                       OR s.student_no LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="status == 'NOT_SUBMITTED'">
                  AND p.id IS NULL
              </if>
              <if test="status == 'PENDING'">
                  AND p.status IN ('PENDING_DEPT', 'DEPT_APPROVED')
              </if>
              <if test="status != null and status != '' and status != 'NOT_SUBMITTED' and status != 'PENDING'">
                  AND p.status = #{status}
              </if>
            ORDER BY s.user_id
            </script>
            """)
    List<DashboardRow> selectDashboard(@Param("campaignId") Long campaignId,
                                       @Param("gradeIds") List<Long> gradeIds,
                                       @Param("collegeId") Long collegeId,
                                       @Param("keyword") String keyword,
                                       @Param("status") String status);
}
