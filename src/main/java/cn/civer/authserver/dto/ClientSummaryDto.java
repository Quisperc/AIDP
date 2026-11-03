package cn.civer.authserver.dto;

/**
 * 客户端列表项与详情响应体，不含 clientSecret。
 *
 * 注意 requirePkce：列表接口走的是 SQL 投影（只取了几列，没有解析 client_settings），
 * 因此列表里该字段恒为 false；详情接口由 Service 从 RegisteredClient 读取，值准确。
 * 前端若要在列表展示 PKCE 状态，应改用逐条详情查询或在此处加 client_settings 解析。
 */
public record ClientSummaryDto(
		String id,
		String clientId,
		String clientName,
		String redirectUri,
		String postLogoutRedirectUri,
		boolean requirePkce) {
}
