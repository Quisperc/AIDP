package cn.civer.client.handler;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/**
 * 下游（认证中心）异常透传。
 *
 * 管理端接口大多只是转发，认证中心返回的是标准 ApiError JSON。
 * 与其在这里解析再重造一份，不如原样把状态码和响应体交给前端，
 * 前端 http.ts 已经会读 payload.message。既少一层转换，也不丢信息。
 */
@RestControllerAdvice
public class ClientApiExceptionHandler {

	@ExceptionHandler(RestClientResponseException.class)
	public ResponseEntity<String> handleDownstream(RestClientResponseException e) {
		int status = e.getStatusCode().value();
		String body = e.getResponseBodyAsString();
		if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
			body = "{\"status\":" + status + ",\"message\":\"认证中心请求失败\"}";
		}
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body);
	}
}
