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
 * 启动时的最小初始化：空库且显式开启时，才写入演示账号。
 *
 * 默认 **关闭**。默认开启的风险不对称：万一某台新机器漏配环境变量，就会带着
 * admin/password 上线；而默认关闭最多让管理员打不开登录页，属于立刻能被发现的故障。
 * 本地开发在 application-secret.yml 里设 app.bootstrap.seed-demo-users=true 即可。
 *
 * 客户端（子系统）不再由代码预置 —— 管理台并入本应用后，第一个管理员登录后
 * 直接走 /admin/clients 注册即可。
 */
@Configuration
public class DataInitializer {

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	/** 是否写入默认演示账号。生产必须保持 false。 */
	@Value("${app.bootstrap.seed-demo-users:false}")
	private boolean seedDemoUsers;

	@Value("${app.bootstrap.demo-user-password:password}")
	private String demoUserPassword;

	@Bean
	public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			if (userRepository.count() > 0) {
				return;
			}
			if (!seedDemoUsers) {
				// 空库 + 未开演示账号 = 没有任何凭据能登录。必须显式提示，
				// 否则现象只是「密码怎么都不对」，排查成本很高。
				log.warn("[bootstrap] 用户表为空且 app.bootstrap.seed-demo-users=false：当前没有任何可登录账号。"
						+ "请在库里手工插入一个 ROLE_ADMIN 用户，或临时开启该开关重启一次。");
				return;
			}
			userRepository.save(new User("admin", passwordEncoder.encode(demoUserPassword), "ROLE_ADMIN"));
			userRepository.save(new User("user", passwordEncoder.encode(demoUserPassword), "ROLE_USER"));
			log.warn("[bootstrap] 已创建默认演示账号 admin / user，口令取自 app.bootstrap.demo-user-password。"
					+ "生产环境请立即改密，或设置 app.bootstrap.seed-demo-users=false 关闭该行为。");
		};
	}
}
