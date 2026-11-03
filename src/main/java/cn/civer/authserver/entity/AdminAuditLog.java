package cn.civer.authserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 管理台操作审计记录：只追加，不修改、不删除。
 *
 * 字段刻意保持扁平（action + target + detail），因为「动作」的种类会持续增加，
 * 每加一种就改表结构不可维护；detail 用来承载该动作自己的上下文。
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor
public class AdminAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 操作者用户名（来自登录会话） */
	@Column(nullable = false)
	private String actor;

	/** 动作码，如 USER_CREATE / CLIENT_DELETE / PROFILE_UPDATE */
	@Column(nullable = false, length = 64)
	private String action;

	/** 操作对象，如被创建的用户名、被删除的 client_id */
	@Column(length = 255)
	private String target;

	/** 该动作的上下文，如 "role=ROLE_ADMIN, enabled=true" */
	@Column(length = 1000)
	private String detail;

	/** 来源 IP，取 X-Forwarded-For 首个地址，取不到则用 remoteAddr */
	@Column(length = 64)
	private String ip;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public AdminAuditLog(String actor, String action, String target, String detail, String ip, Instant createdAt) {
		this.actor = actor;
		this.action = action;
		this.target = target;
		this.detail = detail;
		this.ip = ip;
		this.createdAt = createdAt;
	}
}
