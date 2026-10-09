package com.travelhelper.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Tavily 联网搜索专用 RestClient 配置。
 * 把超时收敛到这一个客户端，避免通过全局 spring.http.client 配置误伤 LLM 的 HTTP 客户端。
 */
@Configuration
public class TavilyConfig {

    /** 连接 + 读取超时，只作用于 Tavily 搜索客户端。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    @Bean("tavilySearchRestClient")
    public RestClient tavilySearchRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return builder.requestFactory(requestFactory).build();
    }
}