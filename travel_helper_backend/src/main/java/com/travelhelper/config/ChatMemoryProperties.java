package com.travelhelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取 application.yml 中 travel-helper.memory.* 配置（多轮对话记忆）。
 */
@Component
@ConfigurationProperties(prefix = "travel-helper.memory")
public class ChatMemoryProperties {

    /** 每个会话保留的最大消息条数（MessageWindowChatMemory 滑动窗口大小，超过后遗忘最早的消息）。 */
    private int maxMessages = 20;

    /** 会话过期天数：超过该天数没有活动（最后一条消息时间戳更早）的会话，会被每天凌晨 2 点的定时任务清理删除。 */
    private int expireDays = 7;

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getExpireDays() {
        return expireDays;
    }

    public void setExpireDays(int expireDays) {
        this.expireDays = expireDays;
    }
}