package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 趋势图数据点
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "趋势数据点")
public class TrendPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "时间标签 (HH:mm)")
    private String time;

    @ApiModelProperty(value = "数值")
    private Double value;

    @ApiModelProperty(value = "时间戳")
    private Long timestamp;
}
