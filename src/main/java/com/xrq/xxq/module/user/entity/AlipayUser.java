package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("alipay_user")
public class AlipayUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;          // FK → user.id
    private String alipayUserId;
}
