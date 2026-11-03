package cn.civer.authserver.dto;

import java.time.Instant;

/**
 * REST 接口的错误响应体。
 * 原先各 Controller 自行决定错误形态（有的返回裸字符串、有的抛异常变成 500），
 * 调用方无法统一处理；现在所有接口错误都是这个形状。
 */
public record ApiError(int status, String error, String message, Instant timestamp) {

	public static ApiError of(int status, String error, String message) {
		return new ApiError(status, error, message, Instant.now());
	}
}
