package cn.civer.client.service;

import cn.civer.client.client.UserFeignClient;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

	private final UserFeignClient userFeignClient;

	public UserService(UserFeignClient userFeignClient) {
		this.userFeignClient = userFeignClient;
	}

	public List<UserDto> getUsers() {
		return userFeignClient.getUsers();
	}

	public UserDto getUser(Long id) {
		return getUsers().stream()
				.filter(u -> u.getId().equals(id))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("User not found"));
	}

	public UserDto createUser(UserDto user) {
		return userFeignClient.createUser(user);
	}

	public void updateUser(Long id, UserDto user) {
		userFeignClient.updateUser(id, user);
	}

	public void deleteUser(Long id) {
		userFeignClient.deleteUser(id);
	}

	public void updateCurrentUserProfile(UserDto user) {
		userFeignClient.updateCurrentUser(user);
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
