package com.travelhelper.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import com.travelhelper.config.ChatMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 会话过期清理的集成测试：造数据 + 直接调清理方法，验证「过期删除、依赖配置天数、删除前日志」。
 * 用独立的内存 H2，避免污染开发用的文件库 data/。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:cleanup-test;DB_CLOSE_DELAY=-1")
@ExtendWith(OutputCaptureExtension.class)
class ChatMemoryCleanupServiceTest {

    @Autowired
    private ChatMemoryCleanupService cleanupService;

    @Autowired
    private ChatMemoryProperties chatMemoryProperties;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY");
    }

    @Test
    void deletesOnlySessionsInactiveLongerThanConfiguredDays() {
        assertThat(chatMemoryProperties.getExpireDays()).isEqualTo(7);

        seed("expired-30d", LocalDateTime.now().minusDays(30));  // 早该过期
        seed("active-1d", LocalDateTime.now().minusDays(1));     // 近期活跃
        // 贴近 7 天窗口边界内（比 7 天前晚 1 分钟 = 6 天 23 小时 59 分），应仍被保留。
        // 不能写成 minusDays(7)：那样会和下面 cleanup 里的 cutoff（now.minusDays(7)）仅差几微秒，
        // 而播种的 now 总比 cleanup 的 now 略早，会被误判为过期，导致测试偶发失败。
        seed("active-7d", LocalDateTime.now().minusDays(7).plusMinutes(1));

        cleanupService.cleanupExpiredSessions(); // 走默认配置（7 天）

        assertThat(conversationIds()).doesNotContain("expired-30d");
        assertThat(conversationIds()).contains("active-1d", "active-7d");
    }

    @Test
    void honorsConfigurableExpireDays() {
        seed("borderline", LocalDateTime.now().minusDays(20));

        cleanupService.cleanupExpiredSessions(30); // 30 天宽限期：20 天仍算活跃
        assertThat(conversationIds()).contains("borderline");

        cleanupService.cleanupExpiredSessions(10); // 10 天宽限期：20 天已过期
        assertThat(conversationIds()).doesNotContain("borderline");
    }

    @Test
    void logsConversationBeforeDeleting(CapturedOutput output) {
        seed("to-delete", LocalDateTime.now().minusDays(30));

        cleanupService.cleanupExpiredSessions(7);

        assertThat(output).contains("conversationId=to-delete");
    }

    private void seed(String conversationId, LocalDateTime lastActive) {
        jdbcTemplate.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)",
                conversationId, "message of " + conversationId, "USER", Timestamp.valueOf(lastActive));
    }

    private List<String> conversationIds() {
        return jdbcTemplate.queryForList("SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY", String.class);
    }
}