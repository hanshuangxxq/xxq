package com.xrq.xxq.module.file.service;

import java.util.Set;

import com.xrq.xxq.module.file.entity.FileBizEnum;

/**
 * 文件用途登记：每个持有文件路径的业务域实现一份，供<b>孤儿成品回收</b>对账。
 * <p>
 * 背景：产物按内容寻址（{@code objects/{biz}/{sha256}{ext}}），同一份物理文件可能被多行业务数据
 * 引用（比如同一份模板论文被两个学生提交）。因此<b>业务删记录时绝不能顺手删文件</b>——
 * 那条路径可能还被别人引用着。删除动作统一由 {@code FileMaintenanceTask} 在
 * 「无任何业务行引用 + 超过宽限期」后执行。
 * <p>
 * <b>新增业务表忘了实现本接口会怎样</b>：该 biz 目录整体跳过回收并记 warn（宁可漏删不可误删）；
 * 且 {@code FileUsageCompletenessTest} 会在构建期直接失败，把问题拦在提交前。
 */
public interface FileUsageContributor {

    /** 本贡献者负责的业务目录（一个贡献者可覆盖多个，如 practice 的 5 个子域）。 */
    Set<FileBizEnum> biz();

    /**
     * 声明本域有哪些表的哪些列存文件路径。
     * <p>用途是构建期守卫对账：扫 {@code information_schema.columns} 找出全库所有文件列，
     * 断言每一列都被某个贡献者认领或命中豁免清单 —— 这样「新加了一张带 file_name 的表却忘了登记」
     * 会在测试阶段暴露，而不是在生产上表现为「磁盘只涨不降」。
     */
    Set<FileSource> sources();

    /**
     * 指定业务目录当前<b>被引用的</b>存储相对路径快照
     * （形如 {@code objects/graduation-thesis/{sha256}.pdf}）。
     * <p>实现应按 {@code file_name IS NOT NULL} 查该 biz 对应的表；逻辑删除的行由
     * {@code @TableLogic} 自动排除，这正是「业务删记录 → 文件进入待回收」的通道。
     */
    Set<String> referencedPaths(FileBizEnum biz);

    /** 一个「表.列」的文件存放位置声明。 */
    record FileSource(String table, String column) {
    }
}
