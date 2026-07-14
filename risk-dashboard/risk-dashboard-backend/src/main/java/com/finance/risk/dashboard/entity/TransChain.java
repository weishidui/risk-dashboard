package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 资金链路追踪实体 — 对应 trans_chain 表 (11字段)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransChain implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String chainId;
    private String transId;
    private String fromUserId;
    private String toUserId;
    private Integer hopOrder;
    private Double amount;
    private Long transTime;
    private Integer chainDepth;
    private Integer isLoop;
    private String createTime;
}
