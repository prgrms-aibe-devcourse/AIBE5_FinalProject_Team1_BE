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
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class);
    private final ApplicationContextRunner redisAutoConfigContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class));
    private final ApplicationContextRunner redisFullContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withUserConfiguration(RedisConfig.class);

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
    @DisplayName("Redis 공통 설정을 끄면 공통 RedisTemplate 빈을 등록하지 않음")
    void doesNotRegisterRedisTemplatesWhenRedisConfigIsDisabled() {
        contextRunner
                .withPropertyValues("app.redis.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
                    assertThat(context).doesNotHaveBean("redisTemplate");
                });
    }

    @Test
    @DisplayName("RedisConnectionFactory가 있어도 Redis 공통 설정을 끄면 공통 빈을 등록하지 않음")
    void disabledRedisConfigDoesNotRegisterTemplatesEvenWhenConnectionFactoryExists() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withPropertyValues("app.redis.enabled=false")
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
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
    @DisplayName("RedisTemplate key serializer는 UTF-8 문자열 왕복 직렬화를 수행함")
    void redisTemplateKeySerializerRoundTripsUtf8String() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
                    @SuppressWarnings("unchecked")
                    RedisSerializer<String> keySerializer =
                            (RedisSerializer<String>) redisTemplate.getKeySerializer();

                    String key = "codedock:redis:키:1";
                    byte[] serializedKey = keySerializer.serialize(key);

                    assertThat(serializedKey).isEqualTo(key.getBytes(StandardCharsets.UTF_8));
                    assertThat(keySerializer.deserialize(serializedKey)).isEqualTo(key);
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
    @DisplayName("RedisTemplate value serializer는 값 객체를 JSON으로 왕복 직렬화함")
    void redisTemplateValueSerializerRoundTripsValueObjectAsJson() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
                    @SuppressWarnings("unchecked")
                    RedisSerializer<Object> valueSerializer =
                            (RedisSerializer<Object>) redisTemplate.getValueSerializer();

                    RedisSampleValue value = new RedisSampleValue("MESSAGE_CREATED", 17L, true);
                    byte[] serializedValue = valueSerializer.serialize(value);

                    assertThat(serializedValue).isNotEmpty();
                    assertThat(new String(serializedValue, StandardCharsets.UTF_8))
                            .contains("MESSAGE_CREATED", "workspaceId", "active");
                    assertThat(valueSerializer.deserialize(serializedValue)).isEqualTo(value);
                });
    }

    @Test
    @DisplayName("RedisTemplate hash value serializer도 값 객체를 JSON으로 왕복 직렬화함")
    void redisTemplateHashValueSerializerRoundTripsValueObjectAsJson() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
                    @SuppressWarnings("unchecked")
                    RedisSerializer<Object> hashValueSerializer =
                            (RedisSerializer<Object>) redisTemplate.getHashValueSerializer();

                    RedisSampleValue value = new RedisSampleValue("REACTION_UPDATED", 33L, false);
                    byte[] serializedValue = hashValueSerializer.serialize(value);

                    assertThat(serializedValue).isNotEmpty();
                    assertThat(hashValueSerializer.deserialize(serializedValue)).isEqualTo(value);
                });
    }

    @Test
    @DisplayName("RedisTemplate은 key와 hash key serializer를 같은 문자열 정책으로 유지함")
    void redisTemplateKeepsSameStringPolicyForKeyAndHashKey() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);

                    assertThat(redisTemplate.getKeySerializer()).isSameAs(redisTemplate.getHashKeySerializer());
                    assertThat(redisTemplate.getKeySerializer()).isSameAs(StringRedisSerializer.UTF_8);
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
    @DisplayName("Spring Redis 자동 설정과 RedisConfig를 함께 사용해도 빈 충돌이 발생하지 않음")
    void redisConfigWorksWithSpringRedisAutoConfiguration() {
        redisFullContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StringRedisTemplate.class);
            assertThat(context).hasBean("redisTemplate");

            RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
            assertThat(redisTemplate.getKeySerializer())
                    .isInstanceOf(StringRedisSerializer.class);
            assertThat(redisTemplate.getValueSerializer())
                    .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        });
    }

    @Test
    @DisplayName("이미 redisTemplate 빈이 있으면 공통 RedisTemplate을 덮어쓰지 않음")
    void doesNotReplaceExistingRedisTemplateBean() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisTemplate<String, Object> existingRedisTemplate = new RedisTemplate<>();
        existingRedisTemplate.setConnectionFactory(connectionFactory);
        existingRedisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .withBean("redisTemplate", RedisTemplate.class, () -> existingRedisTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("redisTemplate", RedisTemplate.class))
                            .isSameAs(existingRedisTemplate);
                    assertThat(context.getBean("redisTemplate", RedisTemplate.class).getKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                });
    }

    @Test
    @DisplayName("이미 StringRedisTemplate 빈이 있으면 공통 StringRedisTemplate을 덮어쓰지 않음")
    void doesNotReplaceExistingStringRedisTemplateBean() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        StringRedisTemplate existingStringRedisTemplate = new StringRedisTemplate(connectionFactory);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .withBean(StringRedisTemplate.class, () -> existingStringRedisTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context.getBean(StringRedisTemplate.class))
                            .isSameAs(existingStringRedisTemplate);
                });
    }

    @Test
    @DisplayName("RedisTemplate 타입 조회 시 StringRedisTemplate과 redisTemplate을 이름으로 구분할 수 있음")
    void redisTemplateTypeLookupKeepsNamedRedisTemplateResolvable() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    assertThat(context.getBeanNamesForType(RedisTemplate.class))
                            .contains("redisTemplate", "stringRedisTemplate");
                    assertThat(context.getBean("redisTemplate"))
                            .isNotInstanceOf(StringRedisTemplate.class);
                    assertThat(context.getBean("stringRedisTemplate"))
                            .isInstanceOf(StringRedisTemplate.class);
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
    @DisplayName("Spring Redis 연결 설정은 빈 비밀번호를 인증 없음으로 유지함")
    void redisConnectionFactoryKeepsBlankPasswordAsNoPassword() {
        redisAutoConfigContextRunner
                .withPropertyValues("spring.data.redis.password=")
                .run(context -> {
                    LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);

                    assertThat(connectionFactory.getPassword()).isNullOrEmpty();
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

    private record RedisSampleValue(
            String type,
            Long workspaceId,
            boolean active
    ) {
    }
}
