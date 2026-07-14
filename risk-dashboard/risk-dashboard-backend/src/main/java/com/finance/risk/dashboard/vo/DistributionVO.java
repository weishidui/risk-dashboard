package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分布图数据项 (饼图/柱状图通用)
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "分布数据项")
public class DistributionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "分类名称")
    private String name;

    @ApiModelProperty(value = "数值")
    private Long value;

    @ApiModelProperty(value = "颜色代码")
    private String color;
}
