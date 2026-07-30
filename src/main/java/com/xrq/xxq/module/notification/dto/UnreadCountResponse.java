package com.xrq.xxq.module.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未读消息数量响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {

    private Integer count;
}
