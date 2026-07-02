package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 用户画像数据接入 DTO — 对应 user_profile 表
 */
@Data
@ApiModel(description = "用户画像数据接入对象")
public class ProfileInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户标识不能为空")
    @ApiModelProperty(value = "用户标识", required = true)
    private String userId;

    @NotNull(message = "近30天日均消费金额不能为空")
    @ApiModelProperty(value = "过去30天单笔平均金额", required = true)
    private Double avgAmt30d;

    @NotBlank(message = "常用城市列表不能为空")
    @ApiModelProperty(value = "过去3个月高频城市 Top3，逗号分隔", required = true, example = "北京,上海,广州")
    private String commonCities;

    @NotBlank(message = "常用设备列表不能为空")
    @ApiModelProperty(value = "用户常用设备 ID 列表，逗号分隔", required = true, example = "D9001,D9002")
    private String commonDevs;

    @NotNull(message = "上次交易时间不能为空")
    @ApiModelProperty(value = "上一笔交易发生的毫秒级时间戳", required = true)
    private Long lastTransTs;

    @ApiModelProperty(value = "上一笔交易发生城市", example = "北京")
    private String lastCity;
}
