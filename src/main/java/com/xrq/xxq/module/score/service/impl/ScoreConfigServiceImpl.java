package com.xrq.xxq.module.score.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.score.entity.ScoreConfig;
import com.xrq.xxq.module.score.mapper.ScoreConfigMapper;
import com.xrq.xxq.module.score.service.ScoreConfigService;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.util.ReferenceValidator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 成绩占比配置服务实现。
 */
@Service
@RequiredArgsConstructor
public class ScoreConfigServiceImpl extends ServiceImpl<ScoreConfigMapper, ScoreConfig> implements ScoreConfigService {

    private final ReferenceValidator referenceValidator;
    private final TeachInfoMapper teachInfoMapper;

    @Override
    public ScoreConfig getByTeachInfo(Long teachInfoId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<ScoreConfig>()
                .eq(ScoreConfig::getTeachInfoId, teachInfoId));
    }

    @Override
    public ScoreConfig upsert(Long teachInfoId, Integer regularRatio, Long userId) {
        if (regularRatio == null || regularRatio < 0 || regularRatio > 100) {
            throw new BusinessException(400, "平时分占比必须在 0-100 之间");
        }
        // 外键存在性校验
        referenceValidator.requireExists(teachInfoMapper, teachInfoId, "授课安排");
        ScoreConfig exist = getByTeachInfo(teachInfoId);
        if (exist == null) {
            // 唯一性预检（DB 唯一约束已移至应用层）
            Long count = baseMapper.selectCount(new LambdaQueryWrapper<ScoreConfig>()
                    .eq(ScoreConfig::getTeachInfoId, teachInfoId));
            if (count != null && count > 0) {
                throw new BusinessException(409, "该授课安排的成绩占比配置已存在");
            }
            ScoreConfig c = new ScoreConfig();
            c.setTeachInfoId(teachInfoId);
            c.setRegularRatio(regularRatio);
            c.setCreateUserId(userId);
            baseMapper.insert(c);
            return c;
        }
        exist.setRegularRatio(regularRatio);
        exist.setCreateUserId(userId);
        baseMapper.updateById(exist);
        return exist;
    }
}
