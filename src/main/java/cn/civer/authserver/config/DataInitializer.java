package cn.civer.authserver.config;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class DataInitializer {

	@Bean
	public CommandLineRunner initData(UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		return args -> {
			// Initialize Users
			if (userRepository.count() == 0) {
				userRepository.save(new User("admin", passwordEncoder.encode("password"), "ROLE_ADMIN"));
				userRepository.save(new User("user", passwordEncoder.encode("password"), "ROLE_USER"));
				System.out.println("Default users created: admin/password, user/password");
			}

			// Client initialization REMOVED per requirements.
			// Default client must be configured externally or via admin tool.
			System.out.println("Client initialization skipped. Please configure clients manually in the database.");
		};
	}
}
