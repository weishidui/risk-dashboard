package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 统一 API 返回结果封装
 * 所有接口使用此对象包装返回，保证前端解析一致性
 *
 * @param <T> 数据泛型
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@ApiModel(description = "统一返回结果")
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    @ApiModelProperty(value = "业务状态码", example = "200")
    private Integer code;

    /** 响应消息 */
    @ApiModelProperty(value = "响应消息", example = "操作成功")
    private String message;

    /** 响应数据 */
    @ApiModelProperty(value = "响应数据")
    private T data;

    /** 时间戳 */
    @ApiModelProperty(value = "响应时间戳")
    private Long timestamp;

    // ==================== 私有构造器 ====================
    private Result() {}

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 成功 - 无数据
     */
    public static <T> Result<T> ok() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功 - 带数据
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功 - 带消息和数据
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 失败 - 使用默认错误码
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败 - 自定义错误码
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 参数校验失败
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 未授权
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    // ==================== Getters/Setters ====================
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
