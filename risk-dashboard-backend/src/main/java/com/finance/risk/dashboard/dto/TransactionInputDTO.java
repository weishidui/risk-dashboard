package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 交易流水数据接入 DTO — 对应 transaction_history 表 (29个业务字段)
 */
@Data
@ApiModel(description = "交易流水数据接入对象")
public class TransactionInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 基础交易信息 (9字段) ==========
    @NotBlank @ApiModelProperty(value = "交易流水号", required = true)
    private String transId;
    @NotBlank @ApiModelProperty(value = "用户标识", required = true)
    private String userId;
    @NotNull @ApiModelProperty(value = "交易金额", required = true)
    private Double amount;
    @NotNull @ApiModelProperty(value = "交易时间戳(13位毫秒)", required = true)
    private Long timestamp;
    @ApiModelProperty(value = "交易城市")
    private String city;
    @ApiModelProperty(value = "地理经纬度")
    private String geoLocation;
    @ApiModelProperty(value = "设备指纹")
    private String deviceId;
    @ApiModelProperty(value = "网络类型 (WiFi/4G/5G/VPN)")
    private String networkType;
    @ApiModelProperty(value = "设备安全分(0-100)")
    private Integer devScore;

    // ========== 设备/环境维度 (10字段) ==========
    @ApiModelProperty(value = "客户端IP地址")
    private String ipAddress;
    @ApiModelProperty(value = "操作系统类型 (Android/iOS/Web)")
    private String osType;
    @ApiModelProperty(value = "系统版本号")
    private String osVersion;
    @ApiModelProperty(value = "屏幕分辨率")
    private String screenResolution;
    @ApiModelProperty(value = "电池电量(0-100)")
    private Integer batteryLevel;
    @ApiModelProperty(value = "是否Root/越狱 (0=否 1=是)")
    private Integer rootJailbreak;
    @ApiModelProperty(value = "SIM卡运营商")
    private String simOperator;
    @ApiModelProperty(value = "浏览器UA")
    private String userAgent;
    @ApiModelProperty(value = "DNS服务器地址")
    private String dnsServer;
    @ApiModelProperty(value = "WiFi名称(脱敏)")
    private String wifiSsid;

    // ========== 交易行为维度 (6字段) ==========
    @ApiModelProperty(value = "交易类型 (同行转账/跨行转账/对公转账)")
    private String transType;
    @ApiModelProperty(value = "支付渠道 (bank_card/balance/wechat/alipay)")
    private String payChannel;
    @ApiModelProperty(value = "输入方式 (manual/paste/autofill)")
    private String inputMethod;
    @ApiModelProperty(value = "页面停留时长(毫秒)")
    private Long clickDuration;
    @ApiModelProperty(value = "转账备注")
    private String note;
    @ApiModelProperty(value = "操作页面URL")
    private String pageUrl;

    // ========== 收款方维度 (3字段) ==========
    @ApiModelProperty(value = "收款方账户ID")
    private String counterpartyId;
    @ApiModelProperty(value = "收款方姓名(脱敏)")
    private String counterpartyName;
    @ApiModelProperty(value = "收款方开户行")
    private String counterpartyBank;

    // ========== 身份/会话维度 (2字段) ==========
    @ApiModelProperty(value = "登录会话ID")
    private String loginSessionId;
    @ApiModelProperty(value = "登录失败次数")
    private Integer loginFailCount;
}
