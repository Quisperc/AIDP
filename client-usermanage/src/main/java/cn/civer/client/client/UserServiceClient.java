package cn.civer.client.client;

import cn.civer.client.service.UserService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * 声明式 HTTP 客户端：调用认证中心用户 API（Spring Boot 4 HTTP Service Client）。
 */
@HttpExchange("/api")
public interface UserServiceClient {

	@GetExchange("/users")
	List<UserService.UserDto> getUsers();

	@PostExchange("/users")
	UserService.UserDto createUser(@RequestBody UserService.UserDto user);

	@PutExchange("/users/{id}")
	UserService.UserDto updateUser(@PathVariable("id") Long id, @RequestBody UserService.UserDto user);

	@DeleteExchange("/users/{id}")
	void deleteUser(@PathVariable("id") Long id);

	@PutExchange("/users/me")
	UserService.UserDto updateCurrentUser(@RequestBody UserService.UserDto user);
}
