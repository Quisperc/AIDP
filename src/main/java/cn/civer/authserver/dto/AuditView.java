package cn.civer.authserver.dto;

import cn.civer.authserver.entity.AdminAuditLog;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 审计记录的展示视图。
 *
 * 时间与动作码在服务端就转成人能读的字符串：模板里既不方便做时区换算，
 * 也不适合维护「动作码 → 中文」的映射表（漏一个就显示成 USER_CREATE 这种原始码）。
 */
public record AuditView(
		long id,
		String time,
		String actor,
		String action,
		String actionLabel,
		String target,
		String detail,
		String ip) {

	private static final DateTimeFormatter TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	public static AuditView from(AdminAuditLog log) {
		return new AuditView(
				log.getId(),
				log.getCreatedAt() == null ? "" : TIME_FORMAT.format(log.getCreatedAt()),
				log.getActor(),
				log.getAction(),
				label(log.getAction()),
				log.getTarget(),
				log.getDetail(),
				log.getIp());
	}

	private static String label(String action) {
		if (action == null) {
			return "";
		}
		return switch (action) {
			case "USER_CREATE" -> "新建用户";
			case "USER_UPDATE" -> "修改用户";
			case "USER_DISABLE" -> "停用用户";
			case "CLIENT_CREATE" -> "注册客户端";
			case "CLIENT_UPDATE" -> "修改客户端";
			case "CLIENT_DELETE" -> "删除客户端";
			case "ACCOUNT_UPDATE" -> "修改账号资料";
			case "AUTHZ_REVOKE" -> "撤销应用授权";
			default -> action;
		};
	}
}
