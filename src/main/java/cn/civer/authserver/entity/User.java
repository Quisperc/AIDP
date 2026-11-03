package cn.civer.authserver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 注意：这里刻意不用 lombok 的 @Data。
 * @Data 会生成包含 password 的 toString()，任何一处 log.info("user={}", user)
 * 都会把密码哈希写进日志；它生成的 equals/hashCode 覆盖全部字段，也不适合 JPA 实体。
 * 对外响应一律使用 {@link cn.civer.authserver.dto.UserResponse}（不含 password）。
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

	public User(String username, String password, String role) {
		this.username = username;
		this.password = password;
		this.role = role;
	}

	/** toString 中不含 password，避免误打日志泄露凭据 */
	@Override
	public String toString() {
		return "User{id=" + id + ", username='" + username + "', role='" + role + "', enabled=" + enabled + "}";
	}
}
