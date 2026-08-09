package com.xrq.xxq.common;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据。 */
    private List<T> records;
    /** 总记录数。 */
    private long total;
    /** 当前页码。 */
    private int page;
    /** 每页条数。 */
    private int pageSize;
    /** 总页数。 */
    private long pages;

    /**
     * 从 MyBatis Plus 分页结果与转换后的当前页记录构造。
     *
     * @param page    MyBatis Plus 分页对象（提供 total/current/size/pages）
     * @param records 已转换为 DTO 的当前页记录
     */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize(), page.getPages());
    }

    /**
     * 内存分页：对已全部加载的列表切片。适用于聚合、多源合并等无法将分页下推到 SQL 的查询。
     * 调用方应在此之前完成排序，以保证跨页全局有序。
     */
    public static <T> PageResult<T> slice(List<T> all, PageQuery pageQuery) {
        int total = all.size();
        int page = pageQuery.resolvedPage();
        int size = pageQuery.resolvedSize();
        int from = (int) Math.min((long) (page - 1) * size, total);
        int to = Math.min(from + size, total);
        int pages = (total + size - 1) / size;
        return new PageResult<>(all.subList(from, to), total, page, size, pages);
    }
}
