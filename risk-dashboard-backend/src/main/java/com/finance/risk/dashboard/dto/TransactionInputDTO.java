package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 交易流水数据接入 DTO — 对应 transaction_history 表
 */
@Data
@ApiModel(description = "交易流水数据接入对象")
public class TransactionInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "交易流水号不能为空")
    @ApiModelProperty(value = "交易流水号 (全局唯一ID)", required = true, example = "TXN20260630120000001")
    private String transId;

    @NotBlank(message = "用户标识不能为空")
    @ApiModelProperty(value = "用户标识 (关联画像库的主键)", required = true, example = "USER10086")
    private String userId;

    @NotNull(message = "交易金额不能为空")
    @ApiModelProperty(value = "交易金额", required = true, example = "15000.00")
    private Double amount;

    @NotNull(message = "交易时间戳不能为空")
    @ApiModelProperty(value = "交易时间戳 (13位毫秒级)", required = true, example = "1719734400000")
    private Long timestamp;

    @ApiModelProperty(value = "交易发生地城市名", example = "北京")
    private String city;

    @ApiModelProperty(value = "地理经纬度坐标", example = "116.3,39.9")
    private String geoLocation;

    @ApiModelProperty(value = "设备指纹 (硬件唯一标识码)", example = "DEVICE_A8F3C2D1")
    private String deviceId;

    @ApiModelProperty(value = "网络类型 (WiFi/4G/5G/VPN)", example = "4G", allowableValues = "WiFi,4G,5G,VPN")
    private String networkType;

    @ApiModelProperty(value = "设备安全分 (0-100)", example = "85")
    private Integer devScore;
}
