package cn.civer.authserver.dto;

/**
 * 客户端新增/修改的入参（管理台表单构造，不再经过 JSON 绑定）。
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
		Boolean requirePkce) {
}
