package cn.civer.authserver.handler;

import cn.civer.authserver.dto.ApiError;
import cn.civer.authserver.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 接口的统一异常处理。
 *
 * 用 @RestControllerAdvice 而非 @ControllerAdvice：只拦截 @RestController，
 * 不影响 Thymeleaf 页面（/login、/oauth2/consent 等）走既有的错误页流程。
 *
 * 刻意不接 AccessDeniedException —— 它由 Spring Security 处理为 403，
 * 若在此处捕获会改变既有的权限拒绝行为。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiError> handleBusiness(BusinessException e) {
		HttpStatus status = e.getStatus();
		log.debug("[api] business error {} : {}", status.value(), e.getMessage());
		return ResponseEntity.status(status)
				.body(ApiError.of(status.value(), status.getReasonPhrase(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(err -> err.getField() + " " + err.getDefaultMessage())
				.orElse("参数校验未通过");
		return ResponseEntity.badRequest()
				.body(ApiError.of(400, "Bad Request", message));
	}

	/** 请求体不是合法 JSON，或类型无法转换 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(ApiError.of(400, "Bad Request", "请求体格式不正确"));
	}

	/**
	 * 兜底：避免把堆栈或未处理异常直接暴露给调用方。
	 *
	 * 必须让 Spring Security 的异常继续往外抛：AccessDeniedException 本该由
	 * ExceptionTranslationFilter 转成 403，一旦在这里被吞掉就会变成 500，
	 * 权限拒绝的语义被破坏（AdminApiAuthorizationTest 正是断言 403，能直接抓到）。
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception e) {
		if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
			throw (RuntimeException) e;
		}
		log.error("[api] unexpected error", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiError.of(500, "Internal Server Error", "服务内部错误，请稍后重试"));
	}
}
