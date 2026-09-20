package com.xrq.xxq.module.clazz.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.clazz.entity.ClassName;

/**
 * 班级服务。
 * <p>
 * 班级是「院系 → 专业 → 班级 → 学生」归属链的中间层：本表只存 {@code major_id}，
 * 院系经 {@code major.college_id} 两跳推导。全应用的「班级 → 专业 / 院系」解析
 * 一律走本接口的方法，不要在调用方手写两跳查询，否则两跳口径会散落各处。
 */
public interface ClassNameService extends IService<ClassName> {

    /** 批量解析 classId -> 班级名 Map（空集合返回空 Map）。 */
    Map<Long, String> toNameMap(Collection<Long> ids);

    /** 单班所属专业 id（classId 为 null / 班级不存在 / 未挂专业时返回 null）。 */
    Long majorIdOf(Long classId);

    /** 批量解析 classId -> majorId；班级不存在或未挂专业的不进 Map。 */
    Map<Long, Long> toMajorIdMap(Collection<Long> classIds);

    /** 单班所属院系 id（经 major.college_id；任一跳缺失返回 null）。 */
    Long collegeIdOf(Long classId);

    /** 批量解析 classId -> collegeId（经 major 两跳）；任一跳缺失的不进 Map。 */
    Map<Long, Long> toCollegeIdMap(Collection<Long> classIds);

    /**
     * 批量解析 班级名 -> collegeId（经 major 两跳）；任一跳缺失的不进 Map。
     * <p>成绩/课表里的 class_name 是逗号分隔的班级名 CSV，按名解析时才用本方法。
     */
    Map<String, Long> toCollegeIdMapByClassName(Collection<String> classNames);

    /** 指定专业下的班级 id（majorIds 为空返回空列表）。 */
    List<Long> classIdsByMajorIds(Collection<Long> majorIds);

    /** 指定院系下的班级 id（经 major.college_id；collegeId 为 null 返回空列表）。 */
    List<Long> classIdsByCollegeId(Long collegeId);

    /** 指定院系下的班级名（经 major.college_id；collegeId 为 null 返回空列表）。 */
    List<String> classNamesByCollegeId(Long collegeId);

    /**
     * 删除班级；该班仍有学生时抛 409 拒绝。
     * <p>
     * 学生的专业与院系全靠班级推导，直接删掉班级会让这些学生的两项归属静默变 NULL
     * （院系管理员的可见范围随 fail-closed 收缩），故必须先安置学生。
     */
    void deleteById(Long id);
}
