package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地理告警视图对象 (用于地图红点展示)
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "地理告警数据")
public class GeoAlertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "告警编号")
    private String alertId;

    @ApiModelProperty(value = "城市名")
    private String city;

    @ApiModelProperty(value = "经度")
    private Double longitude;

    @ApiModelProperty(value = "纬度")
    private Double latitude;

    @ApiModelProperty(value = "风险等级")
    private String riskLevel;

    @ApiModelProperty(value = "触发规则")
    private String hitRules;

    @ApiModelProperty(value = "告警时间")
    private Long alertTime;
}
