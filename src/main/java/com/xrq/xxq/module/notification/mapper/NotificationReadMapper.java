package com.xrq.xxq.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.notification.entity.NotificationRead;
import org.apache.ibatis.annotations.Mapper;

/**
 * 广播已读记录 Mapper。
 */
@Mapper
public interface NotificationReadMapper extends BaseMapper<NotificationRead> {
}
