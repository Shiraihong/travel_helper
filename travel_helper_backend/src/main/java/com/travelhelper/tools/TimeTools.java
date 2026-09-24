package com.travelhelper.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 演示用工具类：提供当前时间查询能力。
 * <p>
 * 用 Spring AI 的 @Tool 注解把方法暴露给模型：当用户问题需要“当前时间”时，
 * 模型会自动发起一次 tool call 调用 {@link #getCurrentTime()}，再把返回结果
 * 作为上下文继续生成最终回答。
 */
public class TimeTools {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取当前本地日期时间。
     *
     * @return 格式为 yyyy-MM-dd HH:mm:ss 的字符串
     */
    @Tool(description = "获取当前的日期和时间，返回 yyyy-MM-dd HH:mm:ss 格式的本地时间字符串")
    public String getCurrentTime() {
        return LocalDateTime.now().format(FORMATTER);
    }
}