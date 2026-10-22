package com.travelhelper.memory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import com.travelhelper.config.ChatMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 多轮对话记忆的过期清理：定时删除「超过 N 天没活动」的会话。
 *
 * 「活动」定义：SPRING_AI_CHAT_MEMORY 表中该 conversation_id 的最新 timestamp（最后一条消息的时间）。
 * 注意 timestamp 列名在两个数据库下写法不同（H2 裸写、PostgreSQL 是保留字需加双引号），
 * 因此按方言动态解析，避免切到 Neon/Postgres 时 SQL 报错。本项目只会用到 H2 与 PostgreSQL 两种库。
 */
@Component
public class ChatMemoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCleanupService.class);

    /** Spring AI 聊天记忆表名（H2 与 PostgreSQL 一致）。 */
    private static final String TABLE = "SPRING_AI_CHAT_MEMORY";

    private final JdbcTemplate jdbcTemplate;
    private final ChatMemoryProperties properties;
    private final String timestampColumn;

    public ChatMemoryCleanupService(DataSource dataSource, ChatMemoryProperties properties) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.properties = properties;
        this.timestampColumn = resolveTimestampColumn(dataSource);
    }

    /** 每天凌晨 2 点执行一次。 */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredSessions() {
        cleanupExpiredSessions(properties.getExpireDays());
    }

    /** 删除「最后活动时间早于 now - expireDays 天」的会话；每删一个会话前先打日志。 */
    public void cleanupExpiredSessions(int expireDays) {
        if (expireDays <= 0) {
            log.warn("会话过期天数配置非法（{}），跳过清理", expireDays);
            return;
        }
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(expireDays));

        int deletedConversations = 0;
        int deletedMessages = 0;
        for (ExpiredSession session : findExpiredSessions(cutoff)) {
            // 删库前先留日志，方便排查被清理的会话
            log.info("清理过期会话：conversationId={}, 最后活动时间={}, 消息条数={}",
                    session.conversationId(), session.lastActive(), session.messageCount());
            deletedMessages += jdbcTemplate.update(
                    "DELETE FROM " + TABLE + " WHERE conversation_id = ?", session.conversationId());
            deletedConversations++;
        }

        log.info("会话过期清理完成：删除会话 {} 个、消息 {} 条（过期天数 {}，截止时间 {}）",
                deletedConversations, deletedMessages, expireDays, cutoff);
    }

    private List<ExpiredSession> findExpiredSessions(Timestamp cutoff) {
        // 位置取列而非列名，规避 H2 大写折叠 / PostgreSQL 小写保留字带来的标签大小写差异
        String sql = "SELECT conversation_id, MAX(" + timestampColumn + ") AS last_active, COUNT(*) AS cnt"
                + " FROM " + TABLE
                + " GROUP BY conversation_id"
                + " HAVING MAX(" + timestampColumn + ") < ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ExpiredSession(
                        rs.getString(1),
                        rs.getTimestamp(2),
                        rs.getInt(3)),
                cutoff);
    }

    /** timestamp 是 PostgreSQL 的保留字，必须用双引号；H2 直接裸写即可。 */
    private String resolveTimestampColumn(DataSource dataSource) {
        return JdbcChatMemoryRepositoryDialect.from(dataSource) instanceof PostgresChatMemoryRepositoryDialect
                ? "\"timestamp\""
                : "timestamp";
    }

    /** 过期会话的查询结果。 */
    private record ExpiredSession(String conversationId, Timestamp lastActive, int messageCount) {
    }
}