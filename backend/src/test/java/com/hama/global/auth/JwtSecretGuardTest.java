package com.hama.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class JwtSecretGuardTest {

    private static final String LOCAL_SECRET = "local-only-dummy-secret-key-for-development-do-not-use-in-production";

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, 1000L, 1000L);
    }

    private static MockEnvironment profile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    @Test
    void dev_에서_커밋된_시크릿이면_부팅을_막는다() {
        assertThatThrownBy(() -> new JwtSecretGuard(properties(LOCAL_SECRET), profile("dev")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("커밋된 값");
    }

    @Test
    void dev_에서_커밋되지_않은_시크릿이면_통과한다() {
        assertThatCode(() -> new JwtSecretGuard(
                properties("a-real-secret-that-only-lives-on-the-server-0123456789"), profile("dev")))
                .doesNotThrowAnyException();
    }

    @Test
    void local_에서는_커밋된_시크릿을_허용한다() {
        assertThatCode(() -> new JwtSecretGuard(properties(LOCAL_SECRET), profile("local")))
                .doesNotThrowAnyException();
    }

    @Test
    void 목록이_실제_yml_값과_일치한다() throws IOException {
        // yml 의 시크릿을 바꾸고 여기 목록을 안 바꾸면, 새 값을 복사해 배포해도 가드가 못 잡습니다.
        assertThat(JwtSecretGuard.COMMITTED_SECRETS)
                .contains(secretIn("application-local.yml"), secretIn("application-test.yml"));
    }

    private static String secretIn(String file) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        return String.valueOf(sources.getFirst().getProperty("jwt.secret"));
    }
}
