package cn.civer.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 认证中心入口。
 *
 * 权限模型：URL 粒度（{@code /admin/**} 需要 ROLE_ADMIN），规则集中在
 * {@code config/SecurityConfig}，控制器上目前没有任何 {@code @PreAuthorize}。
 *
 * 仍然保留 {@link EnableMethodSecurity}：Spring Security 6/7 默认不启用方法级安全，
 * 一旦将来有人按习惯在控制器上补了 {@code @PreAuthorize}，没有这个注解它不会报错、
 * 也不会生效——那是最难发现的一类越权。留着是给后续改动兜底，不是当前有用到。
 */
@SpringBootApplication
@EnableMethodSecurity
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
