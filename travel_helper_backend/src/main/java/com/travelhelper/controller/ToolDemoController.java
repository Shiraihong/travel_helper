package com.travelhelper.controller;

import com.travelhelper.tools.TimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具调用最小 demo：
 * <p>
 * 在构造时通过 {@code defaultTools(new TimeTools())} 把 {@link TimeTools}
 * 注册进 ChatClient。当用户问“现在几点”时，模型会自动调用 getCurrentTime。
 */
@RestController
public class ToolDemoController {

    private final ChatClient chatClient;

    public ToolDemoController(ChatClient.Builder builder) {
        // 关键一步：把 TimeTools 注册到 ChatClient（Spring AI 会扫描其中的 @Tool 方法）
        this.chatClient = builder.defaultTools(new TimeTools()).build();
    }

    /**
     * GET /api/tool/time?q=现在几点
     * <p>
     * q 缺省时默认为“现在几点”，方便直接访问验证。
     */
    @GetMapping(value = "/api/tool/time", produces = "text/plain;charset=UTF-8")
    public String whatTime(@RequestParam(defaultValue = "现在几点") String q) {
        return chatClient.prompt().user(q).call().content();
    }
}