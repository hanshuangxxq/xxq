package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("wx_user")
public class WXUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;       // FK → user.id
    private String wxOpenid;
    private String wxUnionid;
}
