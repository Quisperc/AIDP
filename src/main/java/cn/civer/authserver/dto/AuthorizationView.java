package cn.civer.authserver.dto;

/**
 * 「已授权应用」列表的一行。
 *
 * clientId 是客户端注册时的标识；internalId（UUID）只在数据库层使用，
 * 不暴露到页面上，避免撤销接口拿内部主键做越权探测。
 */
public record AuthorizationView(
		String clientId,
		String clientName,
		String scopes,
		long tokenCount) {
}
