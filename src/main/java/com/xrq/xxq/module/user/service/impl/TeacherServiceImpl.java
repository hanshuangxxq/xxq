package com.xrq.xxq.module.user.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.user.dto.TeacherDto;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.user.service.TeacherService;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl extends ServiceImpl<TeacherMapper, Teacher> implements TeacherService {
    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;
    private final CollegeMapper collegeMapper;

    @Override
    public PageResult<TeacherDto> listTeachers(PageQuery pageQuery) {
        Page<Teacher> page = teacherMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<Teacher>().orderByAsc(Teacher::getId));
        List<Teacher> teachers = page.getRecords();
        if (teachers.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        // 姓名兜底：user.name 列可空，而 Collectors.toMap 遇到 null 值会抛 NPE（不是跳过而是整表失败）
        Map<Long, String> userIdToName = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getName() == null ? "未知" : u.getName(), (a, b) -> a));
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(
                teachers.stream().map(Teacher::getCollegeId).filter(java.util.Objects::nonNull).distinct().toList());

        List<TeacherDto> records = teachers.stream()
                .map(t -> {
                    TeacherDto dto = new TeacherDto();
                    dto.setId(t.getId());
                    dto.setName(userIdToName.getOrDefault(t.getUserId(), "未知"));
                    dto.setTeacherNo(t.getTeacherNo());
                    dto.setTitle(t.getTitle());
                    // college_id 可空（教师未挂院系），空值直接为 null，不拿 null 去查表
                    dto.setDepartment(t.getCollegeId() == null ? null : collegeNameMap.get(t.getCollegeId()));
                    return dto;
                })
                .toList();
        return PageResult.of(page, records);
    }
}
