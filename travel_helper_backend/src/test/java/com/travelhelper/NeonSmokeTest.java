package com.travelhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Neon（PostgreSQL）连通性冒烟测试：
 * - 只有设置了 DATABASE_URL 环境变量时才运行，否则自动跳过（不拖垮其他测试）；
 * - 用 neon profile（application-neon.yml）切换成 PostgreSQL 数据源，真正连一次 Neon。
 *
 * 在家验证：
 *   export DATABASE_URL='postgresql://user:password@host/neondb?sslmode=require'
 *   mvn test -Dtest=NeonSmokeTest
 */
@SpringBootTest
@ActiveProfiles("neon")
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+")
class NeonSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void canConnectToNeon() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(5)).isTrue();
        }
    }
}