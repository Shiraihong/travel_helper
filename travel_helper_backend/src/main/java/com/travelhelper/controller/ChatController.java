package com.travelhelper.controller;

import com.travelhelper.config.ChatProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final ChatProperties chatProperties;

    public ChatController(ChatClient.Builder builder, ChatProperties chatProperties) {
        // ChatClient.Builder 由 Spring AI 自动配置注入
        this.chatClient = builder.build();
        this.chatProperties = chatProperties;
    }

    /**
     * 模式一 / 模式三（同步）：
     *   GET /api/chat?message=推荐一个成都3日游行程
     *   GET /api/chat?message=...&role=travel-advisor   （带 system prompt）
     * 一次性返回 AI 的完整文本回复。
     */
    @GetMapping(value = "/api/chat", produces = "text/plain;charset=UTF-8")
    public String chat(@RequestParam String message,
                       @RequestParam(required = false) String role) {
        return prompt(message, role).call().content();
    }

    /**
     * 模式二（流式，SSE）：
     *   GET /api/chat/stream?message=...
     * 以 text/event-stream 逐块推送 AI 的回复。
     */
    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message,
                                 @RequestParam(required = false) String role) {
        Flux<String> stream = prompt(message, role).stream().content()
                .publishOn(Schedulers.boundedElastic());

        SseEmitter emitter = new SseEmitter();
        stream.subscribe(
                chunk -> {
                    try {
                        emitter.send(chunk);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete);
        return emitter;
    }

    /**
     * 组装提示词：可选地根据 role 注入 system prompt。
     */
    private ChatClient.ChatClientRequestSpec prompt(String message, String role) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (role != null && !role.isBlank()) {
            String systemPrompt = chatProperties.getRoles().get(role.trim());
            if (systemPrompt == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的角色: " + role);
            }
            spec = spec.system(systemPrompt);
        }
        return spec.user(message);
    }
}