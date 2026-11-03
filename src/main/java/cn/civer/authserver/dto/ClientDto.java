package cn.civer.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 客户端新增/修改请求体。
 *
 * requirePkce 为 null 时表示「沿用安全默认值 true」，
 * 避免调用方漏传就退化成关闭 PKCE。
 */
public record ClientDto(
		String clientId,
		String clientSecret,
		String redirectUri,
		String postLogoutRedirectUri,
		String clientName,
		@JsonProperty("requirePkce") Boolean requirePkce) {
}
