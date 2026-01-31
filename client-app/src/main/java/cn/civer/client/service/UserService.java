package cn.civer.client.service;

import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
public class UserService {

	private final WebClient webClient;
	private static final String AUTH_SERVER_URI = "http://127.0.0.1:8080";

	public UserService(WebClient webClient) {
		this.webClient = webClient;
	}

	public List<UserDto> getUsers() {
		return this.webClient.get()
				.uri(AUTH_SERVER_URI + "/api/users")
				.retrieve()
				.bodyToFlux(UserDto.class)
				.collectList()
				.block();
	}

	public UserDto createUser(UserDto user) {
		return this.webClient.post()
				.uri(AUTH_SERVER_URI + "/api/users")
				.bodyValue(user)
				.retrieve()
				.bodyToMono(UserDto.class)
				.block();
	}

	public void updateUser(Long id, UserDto user) {
		this.webClient.put()
				.uri(AUTH_SERVER_URI + "/api/users/" + id)
				.bodyValue(user)
				.retrieve()
				.toBodilessEntity()
				.block();
	}

	public void deleteUser(Long id) {
		this.webClient.delete()
				.uri(AUTH_SERVER_URI + "/api/users/" + id)
				.retrieve()
				.toBodilessEntity()
				.block();
	}

	public void updateCurrentUserProfile(UserDto user) {
		this.webClient.put()
				.uri(AUTH_SERVER_URI + "/api/users/me")
				.bodyValue(user)
				.retrieve()
				.toBodilessEntity()
				.block();
	}

	@Data
	public static class UserDto {
		private Long id;
		private String username;
		private String password;
		private String role;
		private boolean enabled = true;

		// No-args constructor
		public UserDto() {
		}

		public UserDto(String username, String password, String role) {
			this.username = username;
			this.password = password;
			this.role = role;
		}
	}
}
