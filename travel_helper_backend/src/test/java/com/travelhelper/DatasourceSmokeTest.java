package com.travelhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 数据源冒烟测试：验证默认（离线）数据源 H2 能正常建立连接。
 * 不依赖任何外部服务，保证没有 DATABASE_URL / 连不上 Neon 时也能跑。
 */
@SpringBootTest
class DatasourceSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void canConnect() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
        }
    }
}