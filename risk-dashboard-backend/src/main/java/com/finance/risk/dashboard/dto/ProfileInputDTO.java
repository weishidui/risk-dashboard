package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@ApiModel(description = "用户画像数据接入对象")
public class ProfileInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank @ApiModelProperty(value = "用户标识", required = true)
    private String userId;

    // 行为基线
    @NotNull @ApiModelProperty(value = "近30天单笔平均金额", required = true)
    private Double avgAmt30d;
    @NotBlank @ApiModelProperty(value = "常用城市列表", required = true)
    private String commonCities;
    @NotBlank @ApiModelProperty(value = "常用设备列表", required = true)
    private String commonDevs;
    @ApiModelProperty(value = "常用支付渠道")
    private String commonPayChannels;
    @ApiModelProperty(value = "常用交易类型")
    private String commonTransTypes;
    @ApiModelProperty(value = "常用收款方ID列表")
    private String commonCounterparties;

    // 历史快照
    @NotNull @ApiModelProperty(value = "上一笔交易时间戳", required = true)
    private Long lastTransTs;
    @ApiModelProperty(value = "上一笔交易城市")
    private String lastCity;
    @ApiModelProperty(value = "上次登录IP")
    private String lastIp;
    @ApiModelProperty(value = "上次登录时间戳")
    private Long lastLoginTime;

    // 账户信息
    @ApiModelProperty(value = "注册时间戳")
    private Long registrationTime;
    @ApiModelProperty(value = "账户当前余额")
    private Double totalBalance;
    @ApiModelProperty(value = "单笔限额")
    private Double singleLimit;
    @ApiModelProperty(value = "日累计限额")
    private Double dailyLimit;
    @ApiModelProperty(value = "月累计限额")
    private Double monthlyLimit;
    @ApiModelProperty(value = "账户状态(normal/frozen/flagged/dormant)")
    private String accountStatus;

    // 累计统计
    @ApiModelProperty(value = "近24h登录次数")
    private Integer loginCount24h;
    @ApiModelProperty(value = "近24h转账笔数")
    private Integer transCount24h;
    @ApiModelProperty(value = "近24h转账总金额")
    private Double transAmount24h;
    @ApiModelProperty(value = "近7天转账笔数")
    private Integer transCount7d;
    @ApiModelProperty(value = "取消重试次数")
    private Integer cancelRetryCount;

    // 风险标记
    @ApiModelProperty(value = "风险标签(逗号分隔)")
    private String riskTags;
    @ApiModelProperty(value = "用户综合风险评分(-100到100)")
    private Integer riskScore;
}
