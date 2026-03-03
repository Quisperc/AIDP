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

	public UserDto getUser(Long id) {
		return getUsers().stream()
				.filter(u -> u.getId().equals(id))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("User not found"));
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
