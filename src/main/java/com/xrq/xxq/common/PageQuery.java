package com.xrq.xxq.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询入参。
 * <p>
 * 内置默认值与上限，实现"默认限量兜底"：不传参数时按 {@link #DEFAULT_SIZE} 查询，不会全表拉取；
 * 传入 pageSize 超过 {@link #MAX_SIZE} 时截断为上限。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageQuery {

    /** 默认页码（从 1 开始）。 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页条数（不传分页参数时的兜底页大小）。 */
    public static final int DEFAULT_SIZE = 20;
    /** 每页条数上限，防止超大页拖垮数据库。 */
    public static final int MAX_SIZE = 100;

    private Integer page;
    private Integer pageSize;

    /** 规范化页码：null 或小于 1 时取默认值。 */
    public int resolvedPage() {
        return (page == null || page < 1) ? DEFAULT_PAGE : page;
    }

    /** 规范化每页条数：null 或小于 1 取默认值，超过上限则截断。 */
    public int resolvedSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(pageSize, MAX_SIZE);
    }

    /** 构造 MyBatis Plus 分页对象（含 total 查询）。 */
    public <T> Page<T> toPage() {
        return new Page<>(resolvedPage(), resolvedSize());
    }
}
