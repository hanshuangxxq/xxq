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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl extends ServiceImpl<TeacherMapper, Teacher> implements TeacherService {
    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<TeacherDto> listTeachers(PageQuery pageQuery) {
        Page<Teacher> page = teacherMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<Teacher>().orderByAsc(Teacher::getId));
        List<Teacher> teachers = page.getRecords();
        if (teachers.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        Map<Long, String> userIdToName = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        List<TeacherDto> records = teachers.stream()
                .map(t -> {
                    TeacherDto dto = new TeacherDto();
                    dto.setId(t.getId());
                    dto.setName(userIdToName.getOrDefault(t.getUserId(), "未知"));
                    dto.setTeacherNo(t.getTeacherNo());
                    dto.setTitle(t.getTitle());
                    dto.setDepartment(t.getDepartment());
                    return dto;
                })
                .toList();
        return PageResult.of(page, records);
    }
}
