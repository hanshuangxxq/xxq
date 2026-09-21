package com.xrq.xxq.module.coursework.common;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.util.StudentEnrollmentResolver;

import lombok.RequiredArgsConstructor;

/**
 * 授课组解析器：同一「学期 + 课程/活动 + 教师 + 班级名」四元组可能有多条 teach_info 行
 * （多个周时段），内容表只挂<b>锚点行</b>（组内最小 id），写入/读取统一在此归一。
 * <p>
 * 归属校验陷阱：{@code teach_info.teacher_id} 存的是 teacher 子表主键，不是 user.id，
 * 必须经 {@code teacherMapper.findByUserId} 解析后再比较。
 */
@Component
@RequiredArgsConstructor
public class CourseGroupResolver {

    private final TeachInfoMapper teachInfoMapper;
    private final TeacherMapper teacherMapper;
    private final StudentEnrollmentResolver enrollmentResolver;

    /** 任意组内行 id → 锚点行。行不存在 404。 */
    public TeachInfo requireAnchor(Long teachInfoId) {
        if (teachInfoId == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        return pickAnchor(groupRows(info));
    }

    /** 组内取最小 id 行（纯函数，供单测）。 */
    static TeachInfo pickAnchor(List<TeachInfo> group) {
        return group.stream().min(Comparator.comparing(TeachInfo::getId)).orElseThrow();
    }

    /** 组内全部行 id（含锚点自身）。 */
    public List<Long> groupIdsOf(TeachInfo anyRow) {
        return groupRows(anyRow).stream().map(TeachInfo::getId).toList();
    }

    /** 教师归属校验 + 返回锚点行；非本人授课 403。 */
    public TeachInfo requireOwnedAnchor(Long teachInfoId, Long teacherUserId) {
        TeachInfo anchor = requireAnchor(teachInfoId);
        if (!isOwnedBy(anchor, teacherUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        return anchor;
    }

    /** 该授课组是否属于此教师（不抛异常版，供 WS 握手等场景）。 */
    public boolean isOwnedBy(TeachInfo anchor, Long teacherUserId) {
        Teacher t = teacherMapper.findByUserId(teacherUserId);
        return t != null && t.getId().equals(anchor.getTeacherId());
    }

    /** 学生可见性：组内任一行命中选课（常规班名册 / 公选课成员）即可见。 */
    public boolean isVisibleToStudent(Long teachInfoId, Long studentUserId) {
        TeachInfo anchor = requireAnchor(teachInfoId);
        for (Long id : groupIdsOf(anchor)) {
            if (Boolean.TRUE.equals(enrollmentResolver.isEnrolled(id, studentUserId))) {
                return true;
            }
        }
        return false;
    }

    /** 学生不可见 → 403。 */
    public void assertVisibleToStudent(Long teachInfoId, Long studentUserId) {
        if (!isVisibleToStudent(teachInfoId, studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    /** 授课组学生名单（组内各行名单并集去重，user.id 列表）。 */
    public List<Long> rosterUserIds(Long teachInfoId) {
        TeachInfo anchor = requireAnchor(teachInfoId);
        return groupIdsOf(anchor).stream()
                .flatMap(id -> enrollmentResolver.rosterUserIds(id, null).stream())
                .distinct()
                .toList();
    }

    /** 四元组查询组内全部行；可空外键显式 isNull，避免 eq(null) 生成错误 SQL。 */
    private List<TeachInfo> groupRows(TeachInfo info) {
        return teachInfoMapper.selectList(new LambdaQueryWrapper<TeachInfo>()
                .eq(TeachInfo::getSemesterId, info.getSemesterId())
                .eq(TeachInfo::getTeacherId, info.getTeacherId())
                .eq(TeachInfo::getClassName, info.getClassName())
                .eq(info.getCourseId() != null, TeachInfo::getCourseId, info.getCourseId())
                .isNull(info.getCourseId() == null, TeachInfo::getCourseId)
                .eq(info.getCampaignId() != null, TeachInfo::getCampaignId, info.getCampaignId())
                .isNull(info.getCampaignId() == null, TeachInfo::getCampaignId));
    }
}
