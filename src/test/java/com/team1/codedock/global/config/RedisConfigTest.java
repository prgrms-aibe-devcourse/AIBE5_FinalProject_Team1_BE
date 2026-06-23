package com.team1.codedock.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
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
}
