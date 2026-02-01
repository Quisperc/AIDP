package cn.civer.client.client;

import cn.civer.client.service.UserService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "auth-server", url = "${app.auth-server-url:http://127.0.0.1:8080}")
public interface UserFeignClient {

	@GetMapping("/api/users")
	List<UserService.UserDto> getUsers();

	@PostMapping("/api/users")
	UserService.UserDto createUser(@RequestBody UserService.UserDto user);

	@PutMapping("/api/users/{id}")
	UserService.UserDto updateUser(@PathVariable("id") Long id, @RequestBody UserService.UserDto user);

	@DeleteMapping("/api/users/{id}")
	void deleteUser(@PathVariable("id") Long id);

	@PutMapping("/api/users/me")
	UserService.UserDto updateCurrentUser(@RequestBody UserService.UserDto user);
}
