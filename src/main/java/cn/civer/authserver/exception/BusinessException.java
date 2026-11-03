package cn.civer.authserver.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：Service 层抛出，用来表达「规则不满足」，不关心 HTTP。
 *
 * 当前的管理台是服务端渲染的 PRG 流程，Controller 捕获它、把 message 放进 flash
 * 再 302 回列表页（管理员填错一个用户名不该被整页错误打断），所以这里的 status
 * 只作为语义标注，不直接出现在响应里。
 *
 * 如果将来新增 JSON 接口，再补一个 @RestControllerAdvice 按 status 映射即可：
 * 约定已经在这个类型上，不必改动 Service。
 */
public class BusinessException extends RuntimeException {

	private final HttpStatus status;

	private BusinessException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public static BusinessException notFound(String message) {
		return new BusinessException(HttpStatus.NOT_FOUND, message);
	}

	public static BusinessException conflict(String message) {
		return new BusinessException(HttpStatus.CONFLICT, message);
	}

	public static BusinessException badRequest(String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, message);
	}

	public HttpStatus getStatus() {
		return status;
	}
}
