package cn.civer.authserver.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：Service 层抛出，由 GlobalExceptionHandler 统一映射为 HTTP 状态码。
 *
 * 目的是让 Controller 不再散落 ResponseEntity.badRequest() / notFound() 之类的
 * 分支，业务规则集中到 Service，异常到响应的映射集中到一处。
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
