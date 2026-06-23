package com.team1.codedock.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class);
    private final ApplicationContextRunner redisAutoConfigContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class));

    @Test
    @DisplayName("RedisConnectionFactory가 있으면 공통 RedisTemplate 빈을 등록함")
    void registerRedisTemplatesWhenConnectionFactoryExists() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context).hasBean("redisTemplate");
                    assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory())
                            .isSameAs(connectionFactory);
                    assertThat(context.getBean("redisTemplate", RedisTemplate.class).getConnectionFactory())
                            .isSameAs(connectionFactory);
                });
    }

    @Test
    @DisplayName("RedisConnectionFactory가 없으면 공통 RedisTemplate 빈을 등록하지 않음")
    void doesNotRegisterRedisTemplatesWithoutConnectionFactory() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
            assertThat(context).doesNotHaveBean("redisTemplate");
        });
    }

    @Test
    @DisplayName("Redis key와 hash key는 문자열 직렬화를 사용함")
    void redisTemplateUsesStringSerializersForKeys() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);

                    assertThat(redisTemplate.getKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(redisTemplate.getHashKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                });
    }

    @Test
    @DisplayName("Redis value와 hash value는 JSON 직렬화를 사용함")
    void redisTemplateUsesJsonSerializersForValues() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);

                    assertThat(redisTemplate.getValueSerializer())
                            .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
                    assertThat(redisTemplate.getHashValueSerializer())
                            .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
                });
    }

    @Test
    @DisplayName("StringRedisTemplate은 문자열 전용 직렬화를 유지함")
    void stringRedisTemplateKeepsStringSerializers() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    StringRedisTemplate stringRedisTemplate = context.getBean(StringRedisTemplate.class);

                    assertThat(stringRedisTemplate.getKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(stringRedisTemplate.getValueSerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(stringRedisTemplate.getHashKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(stringRedisTemplate.getHashValueSerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                });
    }

    @Test
    @DisplayName("Spring Redis 기본값은 로컬 개발 환경에 맞게 구성됨")
    void redisConnectionFactoryUsesLocalDefaults() {
        redisAutoConfigContextRunner.run(context -> {
            LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);

            assertThat(connectionFactory.getHostName()).isEqualTo("localhost");
            assertThat(connectionFactory.getPort()).isEqualTo(6379);
            assertThat(connectionFactory.getDatabase()).isZero();
            assertThat(connectionFactory.getClientConfiguration().getCommandTimeout())
                    .isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    @DisplayName("Spring Redis 연결 설정은 환경변수 기반 프로퍼티를 반영함")
    void redisConnectionFactoryBindsExternalizedProperties() {
        redisAutoConfigContextRunner
                .withPropertyValues(
                        "spring.data.redis.host=redis",
                        "spring.data.redis.port=6380",
                        "spring.data.redis.password=redis-secret",
                        "spring.data.redis.database=3",
                        "spring.data.redis.timeout=2s",
                        "spring.data.redis.connect-timeout=1500ms"
                )
                .run(context -> {
                    LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);

                    assertThat(connectionFactory.getHostName()).isEqualTo("redis");
                    assertThat(connectionFactory.getPort()).isEqualTo(6380);
                    assertThat(connectionFactory.getDatabase()).isEqualTo(3);
                    assertThat(connectionFactory.getPassword()).isEqualTo("redis-secret");
                    assertThat(connectionFactory.getClientConfiguration().getCommandTimeout())
                            .isEqualTo(Duration.ofSeconds(2));
                    assertThat(connectionFactory.getClientConfiguration().getClientOptions())
                            .hasValueSatisfying(clientOptions -> assertThat(
                                    clientOptions.getSocketOptions().getConnectTimeout()
                            ).isEqualTo(Duration.ofMillis(1500)));
                });
    }

    @Test
    @DisplayName("Spring Redis 빈 생성은 실제 Redis 서버 연결 없이 설정만 검증 가능함")
    void redisConnectionFactoryCreationDoesNotRequireRunningRedisServer() {
        redisAutoConfigContextRunner
                .withPropertyValues(
                        "spring.data.redis.host=redis-not-running.local",
                        "spring.data.redis.port=6399"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LettuceConnectionFactory.class);

                    LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(connectionFactory.getHostName()).isEqualTo("redis-not-running.local");
                    assertThat(connectionFactory.getPort()).isEqualTo(6399);
                });
    }
}
