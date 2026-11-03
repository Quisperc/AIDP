package cn.civer.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 注意：@EnableMethodSecurity 必须显式声明。
 * Spring Security 6/7 默认不启用方法级安全，缺少此注解会导致
 * 各 Controller 上的 @PreAuthorize("hasAuthority('ROLE_ADMIN')") 静默失效
 * （不报错，但权限不生效），届时任意已登录用户均可调用管理接口。
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "cn.civer.authserver.repository")
@EnableMethodSecurity
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
