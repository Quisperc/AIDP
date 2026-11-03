package cn.civer.client.service;

import cn.civer.client.client.UserServiceClient;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

	private final UserServiceClient userServiceClient;

	public UserService(UserServiceClient userServiceClient) {
		this.userServiceClient = userServiceClient;
	}

	public List<UserDto> getUsers() {
		return userServiceClient.getUsers();
	}

	/**
	 * 按 ID 取单个用户。
	 *
	 * 原先实现是 getUsers().stream().filter(...) —— 查一个人要把整张 users 表拉回来再过滤，
	 * 且找不到时抛裸 RuntimeException（最终变成 500）。改为直接调用认证中心
	 * GET /api/users/{id}：不存在时下游返回 404，由 ClientApiExceptionHandler 原样透传。
	 */
	public UserDto getUser(Long id) {
		return userServiceClient.getUser(id);
	}

	public UserDto createUser(UserDto user) {
		return userServiceClient.createUser(user);
	}

	public void updateUser(Long id, UserDto user) {
		userServiceClient.updateUser(id, user);
	}

	public void deleteUser(Long id) {
		userServiceClient.deleteUser(id);
	}

	public void updateCurrentUserProfile(UserDto user) {
		userServiceClient.updateCurrentUser(user);
	}

	@Data
	public static class UserDto {
		private Long id;
		private String username;
		private String password;
		private String role;
		private boolean enabled = true;

		public UserDto() {
		}

		public UserDto(String username, String password, String role) {
			this.username = username;
			this.password = password;
			this.role = role;
		}
	}
}
