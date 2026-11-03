package cn.civer.authserver;

import cn.civer.authserver.config.TokenConfig;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 签名密钥持久化回归测试。
 *
 * 背景：TokenConfig 原先每次启动都在内存随机生成 RSA 密钥对，导致
 * 1) 重启后所有已签发令牌立刻验签失败（全员掉线）；
 * 2) 多实例部署时各实例密钥不同，彼此签发的令牌互相验不了（锁死单实例）。
 *
 * 这里用「新建一个 TokenConfig 实例」来模拟重启：它连的是同一个数据库，
 * 若密钥确实落库，取到的 kid 应与重启前一致。
 */
@SpringBootTest
class SigningKeyPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    @DisplayName("重启后复用同一把签名密钥（kid 不变）")
    void keyIsReusedAfterRestart() throws Exception {
        String kidBefore = firstKeyId(jwkSource);

        // 模拟重启：新的配置实例，同一份数据库
        TokenConfig restarted = new TokenConfig(jdbcTemplate, "primary");
        String kidAfter = firstKeyId(restarted.jwkSource());

        assertEquals(kidBefore, kidAfter,
                "重启后 kid 变了，说明密钥没有真正持久化，已签发令牌会全部失效");
    }

    @Test
    @DisplayName("密钥确实写入了 oauth2_signing_key 表")
    void keyIsPersistedInDatabase() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_signing_key WHERE id = ?", Integer.class, "primary");
        assertEquals(1, count, "库里应恰好有一条 primary 签名密钥");

        String publicPem = jdbcTemplate.queryForObject(
                "SELECT public_key FROM oauth2_signing_key WHERE id = ?", String.class, "primary");
        assertFalse(publicPem.isBlank());
    }

    @Test
    @DisplayName("重复启动不会产生多条密钥记录")
    void repeatedStartupDoesNotDuplicateKeys() {
        new TokenConfig(jdbcTemplate, "primary").jwkSource();
        new TokenConfig(jdbcTemplate, "primary").jwkSource();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_signing_key WHERE id = ?", Integer.class, "primary");
        assertEquals(1, count, "多次启动后仍应只有一条密钥");
    }

    private static String firstKeyId(JWKSource<SecurityContext> source) throws Exception {
        List<JWK> keys = source.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
        assertFalse(keys.isEmpty(), "JWKSet 不应为空");
        return keys.get(0).getKeyID();
    }
}
