package com.xrq.xxq.module.file.entity;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;
import com.xrq.xxq.common.BusinessException;

import lombok.Getter;

/**
 * 文件业务目录白名单：客户端只能传枚举 code，服务端映射到磁盘目录名。
 * <p>
 * 取代旧 {@code ChunkedUploadStore} 的 {@code BIZ_PATTERN} 正则校验 —— 正则只挡字符，
 * 挡不住「客户端自造业务目录」与「把任意扩展名写进存储根」；白名单把目录名收敛为编译期常量，
 * {@code chunks/} 与 {@code objects/} 的一级子目录只能来自 {@link #getCode()}。
 * <p>
 * 注意：本枚举<b>不落库</b>，故不加 {@code @EnumValue}；{@code @JsonValue} 标在 {@code code} 上
 * （取值是目录名/线上标识，不是展示文案）。请求方向不用 Jackson 绑定本枚举 ——
 * DTO 一律用 String 收 {@code biz}，服务端走 {@link #requireCode} 显式解析以给出可读的 400 文案。
 */
@Getter
public enum FileBizEnum {

    /** 毕业论文（graduation_thesis.file_name / file_original）。 */
    GRADUATION_THESIS("graduation-thesis", 20L * 1024 * 1024, Ext.DOC),

    /** 开题报告（graduation_opening_report.file_name）。 */
    GRADUATION_OPENING("graduation-opening-report", 20L * 1024 * 1024, Ext.DOC),

    /** 中期检查材料（graduation_midterm.file_name）。 */
    GRADUATION_MIDTERM("graduation-midterm", 20L * 1024 * 1024, Ext.DOC),

    /** 实习成果报告（internship_report.file_name）。 */
    INTERNSHIP_REPORT("internship-report", 20L * 1024 * 1024, Ext.DOC),

    /** 社会实践报告（social_practice_report.file_name）。 */
    SOCIAL_PRACTICE_REPORT("social-practice-report", 20L * 1024 * 1024, Ext.DOC),

    /** 查重报告附件（graduation_duplicate_check.file_name）。 */
    GRADUATION_DUPLICATE_REPORT("graduation-duplicate-report", 20L * 1024 * 1024, Ext.DOC),

    /** 毕设活动资料（graduation_campaign_material.file_name）。 */
    GRADUATION_CAMPAIGN_MATERIAL("graduation-campaign-material", 20L * 1024 * 1024, Ext.DOC),

    /** 答辩材料附件（graduation_defense.file_name）。 */
    GRADUATION_DEFENSE_MATERIAL("graduation-defense-material", 20L * 1024 * 1024, Ext.DOC),

    /** 竞赛获奖证书（competition_result.file_name），证书是扫描件/照片，放行图片格式。 */
    COMPETITION_CERTIFICATE("competition-certificate", 20L * 1024 * 1024, Ext.CERT);

    /**
     * 扩展名集合持有者。
     * <p><b>为什么不能是枚举自身的静态字段</b>：枚举常量初始化先于枚举类的静态字段，
     * 常量参数里引用本类 {@code static final} 是编译错误（illegal forward reference）；
     * 嵌套类在首次访问时才初始化，常量参数引用它是合法的。
     * <p>
     * 扩展名一律走白名单，不是「任意 1-9 位 alnum」——后者会放行 {@code .jsp}/{@code .html}/
     * {@code .svg}/{@code .sh} 等可执行或可内联脚本的类型，一旦存储目录被误配为静态资源根即成漏洞。
     */
    private static final class Ext {

        /** 文档类允许扩展名（含点、小写），与 {@code PracticeFileService} 既有白名单一致。 */
        private static final Set<String> DOC = Set.of(".doc", ".docx", ".pdf", ".zip", ".rar");

        /** 证书类允许扩展名：扫描件/照片 + PDF。 */
        private static final Set<String> CERT = Set.of(".jpg", ".jpeg", ".png", ".pdf");
    }

    /** 业务目录名（同时是 HTTP 入参/出参取值），落盘为 {@code chunks|objects/{code}/}。 */
    @JsonValue
    private final String code;

    /**
     * 整传（multipart 一次性上传）大小上限，字节。
     * <p>分片路径不适用本上限，统一取 {@code file.max-file-size}（2GB）——这正是「大文件走分片」的入口。
     */
    private final long maxWholeSize;

    /** 本业务允许的扩展名集（含点、小写）。 */
    private final Set<String> extensions;

    FileBizEnum(String code, long maxWholeSize, Set<String> extensions) {
        this.code = code;
        this.maxWholeSize = maxWholeSize;
        this.extensions = extensions;
    }

    /** 本业务允许的扩展名（含点、小写）。 */
    public Set<String> allowedExtensions() {
        return extensions;
    }

    /**
     * 按 code 解析；未命中返回 {@code null}（由调用方决定报错文案，比让 Jackson 抛
     * 「请求体格式错误」更可读）。
     */
    public static FileBizEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FileBizEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 按 code 解析，未命中抛 400。
     * <p>仅接受 {@code code}，不接受枚举名与中文描述 —— 白名单场景下放宽入参只会扩大攻击面。
     */
    public static FileBizEnum requireCode(String code) {
        FileBizEnum biz = fromCode(code);
        if (biz == null) {
            throw new BusinessException(400, "非法的业务目录: " + code);
        }
        return biz;
    }
}
