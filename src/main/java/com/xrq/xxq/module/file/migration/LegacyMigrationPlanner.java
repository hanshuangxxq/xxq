package com.xrq.xxq.module.file.migration;

/**
 * 存量文件迁移的纯决策逻辑（无 I/O、无 Spring），便于独立单测覆盖边界。
 * <p>
 * 迁移把 legacy 扁平目录里的 UUID 文件搬进内容寻址的 {@code objects/{biz}/{sha256}{ext}}，
 * 并把业务表的 {@code file_name} 改指新路径。每行记录的处置由<b>四个事实</b>唯一决定：
 * 旧文件是否还在、旧文件多大、目标产物是否已在库、目标产物多大。
 */
public final class LegacyMigrationPlanner {

    private LegacyMigrationPlanner() {
    }

    public enum Decision {
        /** 旧文件已不在磁盘 —— 记录悬空，不动数据库，只报告待人工处理。 */
        MISSING,

        /** 目标产物已存在且大小一致（同内容早被别的行迁过）—— 只改数据库指向，不复制字节。 */
        DEDUP,

        /** 需要复制字节 + 改数据库。 */
        MIGRATE,

        /**
         * 目标路径已存在但大小不符 —— 内容寻址下同一 sha256 必然同内容，出现大小不符
         * 说明产物被外部损坏或截断。<b>什么都不动</b>：既不覆盖目标（可能还有别的行在用），
         * 也不改库，留待人工核对。
         */
        CONFLICT
    }

    /**
     * @param legacyExists 旧文件在 legacy 目录中存在
     * @param legacySize   旧文件字节数
     * @param targetExists 内容寻址目标路径已存在
     * @param targetSize   目标字节数（{@code targetExists} 为 false 时无意义）
     */
    public static Decision decide(boolean legacyExists, long legacySize,
                                  boolean targetExists, long targetSize) {
        if (!legacyExists) {
            return Decision.MISSING;
        }
        if (targetExists) {
            return targetSize == legacySize ? Decision.DEDUP : Decision.CONFLICT;
        }
        return Decision.MIGRATE;
    }
}
