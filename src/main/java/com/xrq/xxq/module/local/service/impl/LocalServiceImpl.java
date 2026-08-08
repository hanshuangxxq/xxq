package com.xrq.xxq.module.local.service.impl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.local.entity.Local;
import com.xrq.xxq.module.local.entity.LocalTypeEnum;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.local.service.LocalService;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.TeacherNameResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocalServiceImpl extends ServiceImpl<LocalMapper, Local> implements LocalService {

    private final LocalMapper localMapper;
    private final TeacherMapper teacherMapper;
    private final ReferenceValidator referenceValidator;
    private final TeacherNameResolver teacherNameResolver;

    @Override
    public boolean save(Local local) {
        validateManager(local);
        return super.save(local);
    }

    @Override
    public boolean updateById(Local local) {
        validateManager(local);
        return super.updateById(local);
    }

    @Override
    public List<Local> list() {
        List<Local> locals = super.list();
        fillManagerName(locals);
        return locals;
    }

    @Override
    public List<Local> list(Wrapper<Local> queryWrapper) {
        List<Local> locals = super.list(queryWrapper);
        fillManagerName(locals);
        return locals;
    }

    @Override
    public Local getById(Serializable id) {
        Local local = super.getById(id);
        if (local != null) {
            fillManagerName(List.of(local));
        }
        return local;
    }

    /**
     * 校验管理者：实验室/机房必填；填写时校验教师存在。
     */
    private void validateManager(Local local) {
        LocalTypeEnum type = local.getType();
        if ((type == LocalTypeEnum.LABORATORY || type == LocalTypeEnum.COMPUTER_ROOM)
                && local.getManagerId() == null) {
            throw new BusinessException(400, "实验室/机房必须指定管理者");
        }
        referenceValidator.requireExists(teacherMapper, local.getManagerId(), "管理者");
    }

    /**
     * 批量回显管理者姓名（teacher.id -> user.name 两跳解析）。
     */
    private void fillManagerName(List<Local> locals) {
        List<Long> managerIds = locals.stream()
                .map(Local::getManagerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (managerIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = teacherNameResolver.namesByIds(managerIds);
        locals.forEach(l -> l.setManagerName(nameMap.get(l.getManagerId())));
    }
}
