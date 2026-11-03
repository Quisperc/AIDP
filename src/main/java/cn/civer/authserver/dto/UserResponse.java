package cn.civer.authserver.dto;

import cn.civer.authserver.entity.User;

/**
 * 用户接口对外响应体。
 *
 * 这里不存在 password 字段 —— 原先接口直接返回 {@link User} 实体，
 * 一旦漏掉序列化注解就会把凭据返回出去。现在从类型上就不可能泄露。
 */
public record UserResponse(Long id, String username, String role, boolean enabled) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
	}
}
