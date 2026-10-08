package com.travelhelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取 application.yml 中 tavily.* 配置。
 */
@Component
@ConfigurationProperties(prefix = "tavily")
public class TavilyProperties {

    /** Tavily API Key，来自环境变量 TAVILY_API_KEY。 */
    private String apiKey;

    /** Tavily 搜索接口基础地址。 */
    private String baseUrl = "https://api.tavily.com";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}