package cn.civer.authserver.dto;

/**
 * 客户端列表项与详情响应体，不含 clientSecret。
 *
 * requirePkce 两条路径都能给出准确值：详情由 Service 从 RegisteredClient 读取；
 * 列表由 SQL 投影带出 client_settings 后解析 JSON 得出（早期版本没取这一列，
 * 列表里恒为 false，前端只能显示「需查看详情」）。
 */
public record ClientSummaryDto(
		String id,
		String clientId,
		String clientName,
		String redirectUri,
		String postLogoutRedirectUri,
		boolean requirePkce) {
}
