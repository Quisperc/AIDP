package cn.civer.authserver.dto;

/**
 * 客户端新增/修改的入参（管理台表单构造，不再经过 JSON 绑定）。
 *
 * requirePkce 为 null 时：注册按安全默认值 true，更新则沿用该客户端原值。
 * 表单用显式 true/false 的 select 提交，避免复选框「未勾选不提交」的歧义。
 */
public record ClientDto(
		String clientId,
		String clientSecret,
		String redirectUri,
		String postLogoutRedirectUri,
		String clientName,
		Boolean requirePkce) {
}
