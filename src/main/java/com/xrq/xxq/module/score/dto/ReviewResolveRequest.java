package com.xrq.xxq.module.score.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 教务终审核复核申请：可附带调整后的总评成绩（可选），并锁定成绩。
 */
@Data
public class ReviewResolveRequest {

    private String reply;           // 教务回复
    private BigDecimal newTotalScore; // 调整后的总评（可选）
    private boolean resolved;       // true:RESOLVED 已解决 / false:REJECTED 已驳回
}
