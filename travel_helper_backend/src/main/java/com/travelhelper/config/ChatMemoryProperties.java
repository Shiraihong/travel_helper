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

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }
}