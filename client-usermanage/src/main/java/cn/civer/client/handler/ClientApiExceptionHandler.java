package cn.civer.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/**
 * 管理端 REST 接口的统一异常处理。
 *
 * 管理端大量接口只是把请求转发给认证中心，下游返回 401/403/409 时，
 * 原先异常直接冒泡到 DispatcherServlet，前端只看到一个 500，完全不知道
 * 是「登录过期」还是「重名」。这里按状态给出可读提示。
 */
@RestControllerAdvice
public class ClientApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ClientApiExceptionHandler.class);

	@ExceptionHandler(RestClientResponseException.class)
	public ResponseEntity<ApiError> handleDownstream(RestClientResponseException e) {
		int status = e.getStatusCode().value();
		log.debug("[admin-api] downstream returned {}", status);
		return switch (status) {
			case 401 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(ApiError.of(401, "Unauthorized", "登录状态已失效，请重新登录"));
			case 403 -> ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(ApiError.of(403, "Forbidden", "没有权限执行此操作"));
			case 404 -> ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(ApiError.of(404, "Not Found", "请求的资源不存在"));
			case 409 -> ResponseEntity.status(HttpStatus.CONFLICT)
					.body(ApiError.of(409, "Conflict", extractMessage(e, "资源已存在或数据冲突")));
			default -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
					.body(ApiError.of(502, "Bad Gateway", extractMessage(e, "认证中心返回错误")));
		};
	}

	private static String extractMessage(RestClientResponseException e, String fallback) {
		try {
			String body = e.getResponseBodyAsString();
			if (body != null && body.contains("\"message\"")) {
				// 认证中心返回的是 ApiError JSON，直接把 message 透出来
				int i = body.indexOf("\"message\"");
				int start = body.indexOf('"', i + 9);
				if (start > 0) {
					int end = body.indexOf('"', start + 1);
					if (end > start) {
						return body.substring(start + 1, end);
					}
				}
			}
		} catch (Exception ignored) {
			// 解析不了就用兜底文案
		}
		return fallback;
	}

	public record ApiError(int status, String error, String message) {

		public static ApiError of(int status, String error, String message) {
			return new ApiError(status, error, message);
		}
	}
}
