package com.team1.codedock.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 마이그레이션 전략 설정
 * repair() 선행 실행으로 실패 기록을 정리한 뒤 migrate()를 수행한다.
 * repair()는 멱등적으로 동작하므로 항상 포함해도 무방하다.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
