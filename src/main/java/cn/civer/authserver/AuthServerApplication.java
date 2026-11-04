package cn.civer.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * 认证中心入口。
 *
 * 权限模型是 URL 粒度：{@code /admin/**} 需要 ROLE_ADMIN，{@code /account/**} 只需登录，
 * 规则全部集中在 {@code config/SecurityConfig}。代码里没有任何方法级安全注解，
 * 因此不启用 {@code @EnableMethodSecurity}——留一个不生效的开关只会让人误以为
 * 方法级注解有保护。将来确实需要方法级控制时，连同注解和覆盖它的测试一起加。
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
