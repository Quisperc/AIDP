package cn.civer.authserver.config;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 启动时的最小初始化：只在库里没有用户时写入演示账号。
 *
 * 客户端（子系统）不再由代码预置 —— 管理台并入本应用后，第一个管理员
 * 登录后直接走 /admin/clients 注册即可，代码里那套
 * 「initial-client 的 secret 必须与客户端侧配置一致」的隐式约定也随之消失。
 */
@Configuration
public class DataInitializer {

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	/** 是否写入默认演示账号。生产环境应设为 false 并自行初始化账号。 */
	@Value("${app.bootstrap.seed-demo-users:true}")
	private boolean seedDemoUsers;

	@Value("${app.bootstrap.demo-user-password:password}")
	private String demoUserPassword;

	@Bean
	public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			if (!seedDemoUsers) {
				log.info("[bootstrap] seed-demo-users=false, skip default users");
				return;
			}
			if (userRepository.count() > 0) {
				return;
			}
			userRepository.save(new User("admin", passwordEncoder.encode(demoUserPassword), "ROLE_ADMIN"));
			userRepository.save(new User("user", passwordEncoder.encode(demoUserPassword), "ROLE_USER"));
			// 默认口令是安全事故的常见源头，必须醒目提示
			log.warn("[bootstrap] 已创建默认演示账号 admin / user，口令取自 app.bootstrap.demo-user-password。"
					+ "生产环境请立即改密，或设置 app.bootstrap.seed-demo-users=false 关闭该行为。");
		};
	}
}
