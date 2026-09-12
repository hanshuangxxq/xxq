package com.xrq.xxq.module.notification.notice;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 实践与创新域通知场景（竞赛/社会实践/实习/毕业设计）。方法体为空：
 * 通知声明在注解上，由 {@link NotifyAspect} 切面负责发送。
 * <p>
 * 注意：场景方法必须经 Spring 代理调用（业务类注入本组件后调用），禁止同类内自调用（绕过代理不会发通知）。
 */
@Component
public class PracticeNoticeScenes {

    // ---- 竞赛 ----

    /** 竞赛报名审核结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'竞赛报名审核结果'",
            content = "'您的竞赛《' + #competitionName + '》报名' + (#approved ? '已通过' : '已被驳回') + '。'")
    public void competitionReviewed(Long studentUserId, String competitionName, Boolean approved) {
    }

    /** 竞赛获奖通知。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'竞赛结果通知'",
            content = "'您在竞赛《' + #competitionName + '》中获奖：' + #awardDescription + '。'")
    public void competitionAwarded(Long studentUserId, String competitionName, String awardDescription) {
    }

    // ---- 社会实践 ----

    /** 社会实践申报审核结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'社会实践申报审核结果'",
            content = "'您的实践《' + #practiceTitle + '》申报' + (#approved ? '已通过' : '已被驳回') + '。'")
    public void socialPracticeReviewed(Long studentUserId, String practiceTitle, Boolean approved) {
    }

    /** 社会实践报告评审结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'社会实践报告评审结果'",
            content = "'您的实践《' + #practiceTitle + '》报告已评审完成。'")
    public void socialPracticeReportReviewed(Long studentUserId, String practiceTitle) {
    }

    // ---- 实习 ----

    /** 实习报名审核结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'实习报名审核结果'",
            content = "'您的实习《' + #internshipTitle + '》报名' + (#approved ? '已通过' : '已被驳回') + '。'")
    public void internshipReviewed(Long studentUserId, String internshipTitle, Boolean approved) {
    }

    /** 实习报告评审结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'实习报告评审结果'",
            content = "'您的实习《' + #internshipTitle + '》报告已评审完成。'")
    public void internshipReportReviewed(Long studentUserId, String internshipTitle) {
    }

    // ---- 毕业设计 ----

    /** 自拟选题两级审批结果（院系初审/教务终审）。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'毕业选题审批结果'",
            content = "'您的选题《' + #proposalTitle + '》' + #stageDescription"
                    + " + (#approve ? '已通过。' : '被驳回：' + #comment)")
    public void proposalReviewed(Long studentUserId, String proposalTitle, String stageDescription,
                                 Boolean approve, String comment) {
    }

    /** 教师自选学生成功。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'毕业选题匹配结果'",
            content = "'教师已选择你作为指导对象，指导教师：' + #teacherName + '。'")
    public void teacherPicked(Long studentUserId, String teacherName) {
    }

    /** 院系指定分配指导教师。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'毕业选题匹配结果'",
            content = "'院系已为你指定指导教师：' + #teacherName + '。'")
    public void deptAllocated(Long studentUserId, String teacherName) {
    }

    /** 院系改派指导教师。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'毕业选题改派通知'",
            content = "'你的指导教师已调整为：' + #newTeacherName + '，原因：' + #reason")
    public void reassigned(Long studentUserId, String newTeacherName, String reason) {
    }

    /** 开题报告审核结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'开题报告审核结果'",
            content = "'你的开题报告《' + #reportTitle + '》' + (#approve ? '已通过。' : '被退回修改：' + #comment)")
    public void openingReportReviewed(Long studentUserId, String reportTitle, Boolean approve, String comment) {
    }

    /** 中期检查评审结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'中期检查评审结果'",
            content = "'你的中期检查结论：' + #conclusionDescription"
                    + " + (#comment != null ? '，' + #comment : '') + '。'")
    public void midtermReviewed(Long studentUserId, String conclusionDescription, String comment) {
    }

    /** 论文形式审查结果（通过进入查重/退回修改）。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'毕业论文形式审查结果'",
            content = "'你的论文《' + #thesisTitle + '》'"
                    + " + (#approve ? '已通过形式审查，进入查重环节。' : '被退回修改：' + #comment)")
    public void thesisFormReviewed(Long studentUserId, String thesisTitle, Boolean approve, String comment) {
    }

    /** 论文查重结果。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'论文查重结果'",
            content = "'你的论文《' + #thesisTitle + '》查重' + (#pass"
                    + " ? '通过（重复率 ' + #duplicateRate + '%），可进入答辩环节。'"
                    + " : '不通过（重复率 ' + #duplicateRate + '%），请在规定时间内修改后重新提交。')")
    public void thesisDuplicateChecked(Long studentUserId, String thesisTitle, Boolean pass, Integer duplicateRate) {
    }

    /** 毕设总评成绩发布。 */
    @NotifyUser(type = NotificationTypeEnum.PRACTICE, userId = "#studentUserId",
            title = "'毕设成绩发布'",
            content = "'你的毕业设计总评成绩已发布：' + #totalScore + '分'"
                    + " + (#campaignName != null ? '（' + #campaignName + '）' : '') + '。'")
    public void defenseScorePublished(Long studentUserId, BigDecimal totalScore, String campaignName) {
    }
}
