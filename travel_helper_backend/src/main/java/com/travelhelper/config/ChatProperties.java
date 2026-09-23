package com.travelhelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 读取 application.yml 中 travel-helper.* 配置。
 * roles 维护了 role -> system prompt 的映射，key 使用英文。
 */
@Component
@ConfigurationProperties(prefix = "travel-helper")
public class ChatProperties {

    private final Map<String, String> roles = new HashMap<>();

    public Map<String, String> getRoles() {
        return roles;
    }
}