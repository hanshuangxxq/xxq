package com.xrq.xxq.module.user.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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
    public List<TeacherDto> listTeachers() {
        Map<Long, String> userIdToName = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        return teacherMapper.selectList(null).stream()
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
    }
}
