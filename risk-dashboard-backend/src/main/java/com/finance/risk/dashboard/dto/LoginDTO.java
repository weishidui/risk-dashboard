package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
@ApiModel(description = "登录请求")
public class LoginDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank @ApiModelProperty(value = "用户名", required = true)
    private String username;
    @NotBlank @ApiModelProperty(value = "密码", required = true)
    private String password;
}
