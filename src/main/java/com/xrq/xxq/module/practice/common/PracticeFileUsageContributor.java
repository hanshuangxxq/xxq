package com.xrq.xxq.module.practice.common;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileUsageContributor;
import com.xrq.xxq.module.practice.competition.entity.CompetitionResult;
import com.xrq.xxq.module.practice.competition.mapper.CompetitionResultMapper;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaignMaterial;
import com.xrq.xxq.module.practice.graduation.entity.GraduationDefense;
import com.xrq.xxq.module.practice.graduation.entity.GraduationDuplicateCheck;
import com.xrq.xxq.module.practice.graduation.entity.GraduationMidterm;
import com.xrq.xxq.module.practice.graduation.entity.GraduationOpeningReport;
import com.xrq.xxq.module.practice.graduation.entity.GraduationThesis;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMaterialMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationDefenseMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationDuplicateCheckMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationMidtermMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationOpeningReportMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationThesisMapper;
import com.xrq.xxq.module.practice.internship.entity.InternshipReport;
import com.xrq.xxq.module.practice.internship.mapper.InternshipReportMapper;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeReport;
import com.xrq.xxq.module.practice.socialpractice.mapper.SocialPracticeReportMapper;

import lombok.RequiredArgsConstructor;

/**
 * practice 域的文件用途登记：9 张表的 {@code file_name} 列
 * （论文/开题/中期/实习报告/社会实践报告/查重报告/活动资料/答辩材料/获奖证书）。
 * <p>
 * 每个业务目录恰好对应一张表，因此 {@link #referencedPaths} 可直接按 biz 分派。
 * 查的是 {@code file_name} 一列而非整行 —— 大表全量拉回的代价没必要付。
 * 逻辑删除行（{@code deleted=1}）由 {@code @TableLogic} 自动排除，这正是
 * 「业务删记录 → 产物变成无引用 → 由回收任务清理」的通道。
 */
@Component
@RequiredArgsConstructor
public class PracticeFileUsageContributor implements FileUsageContributor {

    private static final Set<FileSource> SOURCES = Set.of(
            new FileSource("graduation_thesis", "file_name"),
            new FileSource("graduation_opening_report", "file_name"),
            new FileSource("graduation_midterm", "file_name"),
            new FileSource("internship_report", "file_name"),
            new FileSource("social_practice_report", "file_name"),
            new FileSource("graduation_duplicate_check", "file_name"),
            new FileSource("graduation_campaign_material", "file_name"),
            new FileSource("graduation_defense", "file_name"),
            new FileSource("competition_result", "file_name"));

    private static final Set<FileBizEnum> BIZ = Set.of(
            FileBizEnum.GRADUATION_THESIS,
            FileBizEnum.GRADUATION_OPENING,
            FileBizEnum.GRADUATION_MIDTERM,
            FileBizEnum.INTERNSHIP_REPORT,
            FileBizEnum.SOCIAL_PRACTICE_REPORT,
            FileBizEnum.GRADUATION_DUPLICATE_REPORT,
            FileBizEnum.GRADUATION_CAMPAIGN_MATERIAL,
            FileBizEnum.GRADUATION_DEFENSE_MATERIAL,
            FileBizEnum.COMPETITION_CERTIFICATE);

    private final GraduationThesisMapper thesisMapper;
    private final GraduationOpeningReportMapper openingReportMapper;
    private final GraduationMidtermMapper midtermMapper;
    private final InternshipReportMapper internshipReportMapper;
    private final SocialPracticeReportMapper socialPracticeReportMapper;
    private final GraduationDuplicateCheckMapper duplicateCheckMapper;
    private final GraduationCampaignMaterialMapper campaignMaterialMapper;
    private final GraduationDefenseMapper defenseMapper;
    private final CompetitionResultMapper competitionResultMapper;

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
            case GRADUATION_THESIS -> collect(thesisMapper, GraduationThesis::getFileName);
            case GRADUATION_OPENING -> collect(openingReportMapper, GraduationOpeningReport::getFileName);
            case GRADUATION_MIDTERM -> collect(midtermMapper, GraduationMidterm::getFileName);
            case INTERNSHIP_REPORT -> collect(internshipReportMapper, InternshipReport::getFileName);
            case SOCIAL_PRACTICE_REPORT -> collect(socialPracticeReportMapper, SocialPracticeReport::getFileName);
            case GRADUATION_DUPLICATE_REPORT -> collect(duplicateCheckMapper, GraduationDuplicateCheck::getFileName);
            case GRADUATION_CAMPAIGN_MATERIAL ->
                    collect(campaignMaterialMapper, GraduationCampaignMaterial::getFileName);
            case GRADUATION_DEFENSE_MATERIAL -> collect(defenseMapper, GraduationDefense::getFileName);
            case COMPETITION_CERTIFICATE -> collect(competitionResultMapper, CompetitionResult::getFileName);
        };
    }

    /** 只取目标列、跳过空值；返回的是业务表里现存的存储路径全集。 */
    private static <T> Set<String> collect(BaseMapper<T> mapper, SFunction<T, String> column) {
        return mapper.selectList(new LambdaQueryWrapper<T>()
                        .select(column)
                        .isNotNull(column)).stream()
                .map(column)
                .filter(path -> path != null && !path.isBlank())
                .collect(Collectors.toSet());
    }
}
