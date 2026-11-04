package cn.civer.authserver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 注意：这里刻意不用 lombok 的 @Data。
 * @Data 会生成包含 password 的 toString()，任何一处 log.info("user={}", user)
 * 都会把密码哈希写进日志；它生成的 equals/hashCode 覆盖全部字段，也不适合 JPA 实体。
 * 对外响应一律使用 {@link cn.civer.authserver.dto.UserResponse}（不含 password）。
 *
 * public_id 是 OIDC 的 sub，也是 oauth2_authorization.principal_name 的取值：
 * 创建时一次性生成、永不改变。username 只是可变的登录名，昵称只用于展示；
 * 三者分离后，改名不会影响客户端按 (iss, sub) 建立的账号关联。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 不可变主体标识（UUID），OIDC sub / principal_name 均取此值 */
	@Column(unique = true)
	private String publicId;

	@Column(unique = true, nullable = false)
	private String username;

	// WRITE_ONLY = 只写：请求体里可以传 password，响应时绝不输出。
	// 注意不能用 @JsonIgnore —— 它会连反序列化一起忽略，导致创建用户时收不到密码
	// （已由 UserApiSerializationTest 覆盖：换成 @JsonIgnore 时该测试会失败）。
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String role; // e.g., ROLE_USER, ROLE_ADMIN

	@Column(nullable = false)
	private boolean enabled = true;

	/** email scope 的 claim 来源。自助填写视为未验证，管理员录入视为已验证。 */
	private String email;

	/** profile scope 的 nickname / preferred_username claim 来源。 */
	private String nickname;

	@Column(nullable = false)
	private boolean emailVerified = false;

	public User(String username, String password, String role) {
		this.username = username;
		this.password = password;
		this.role = role;
	}

	/** 新实体落库前补上不可变主体标识；存量行由 DataInitializer 回填。 */
	@PrePersist
	void ensurePublicId() {
		if (publicId == null || publicId.isBlank()) {
			publicId = UUID.randomUUID().toString();
		}
	}

	/** toString 中不含 password，避免误打日志泄露凭据 */
	@Override
	public String toString() {
		return "User{id=" + id + ", publicId='" + publicId + "', username='" + username
				+ "', role='" + role + "', enabled=" + enabled + "}";
	}
}
