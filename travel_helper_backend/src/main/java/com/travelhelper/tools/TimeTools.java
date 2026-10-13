package com.travelhelper.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 时间工具：返回当前日期和时间。模型回答「现在几点 / 今天几号」等时间相关问题时调用。
 */
@Component
public class TimeTools {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool(description = "获取当前的日期和时间，用于回答「现在几点」「今天几号」「星期几」等时间相关问题")
    public String getCurrentTime() {
        return "当前时间：" + LocalDateTime.now().format(FORMATTER);
    }
}