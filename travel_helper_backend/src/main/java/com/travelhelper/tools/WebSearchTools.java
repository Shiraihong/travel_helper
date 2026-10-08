package com.travelhelper.tools;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.travelhelper.config.TavilyProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tavily 联网搜索工具：把实时搜索结果整理成适合 LLM 阅读的纯文本后返回。
 * 模型在需要「景点、攻略、实时信息」等外部知识时自动调用 {@link #search(String)}。
 */
@Component
public class WebSearchTools {

    /** 返回给 LLM 的最多结果条数。 */
    private static final int MAX_RESULTS = 5;

    /** 每条摘要的最大字数，防止单条异常长撑爆上下文。 */
    private static final int MAX_SNIPPET_LENGTH = 250;

    private final TavilyProperties properties;
    private final RestClient restClient;

    public WebSearchTools(TavilyProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Tool(description = "联网搜索景点、攻略、实时信息等外部知识；返回相关网页的标题、摘要和链接")
    public String search(@ToolParam(description = "搜索关键词，建议使用中文") String query) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return "未配置 TAVILY_API_KEY，无法联网搜索。";
        }

        Map<String, Object> body = Map.of(
                "query", query,
                "search_depth", "basic",
                "max_results", 5,
                "language", "zh");

        try {
            TavilyResponse response = this.restClient.post()
                    .uri(properties.getBaseUrl() + "/search")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TavilyResponse.class);

            return format(response, query);
        }
        catch (Exception e) {
            return "联网搜索失败：" + e.getMessage() + "（查询：\"" + query + "\"）";
        }
    }

    /** 把 Tavily 返回整理成 LLM 易读的文本：去重、截断、只保留核心字段。 */
    private String format(TavilyResponse response, String query) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "未找到与「" + query + "」相关的搜索结果。";
        }

        List<TavilyResult> results = dedup(response.results());

        StringBuilder sb = new StringBuilder("关于「").append(query).append("」的搜索结果：\n");
        int i = 1;
        for (TavilyResult r : results) {
            if (i > MAX_RESULTS) {
                break;
            }
            sb.append(i++).append(". ").append(r.title() == null ? "" : r.title()).append("\n");
            sb.append("　 链接：").append(r.url() == null ? "" : r.url()).append("\n");
            if (r.content() != null && !r.content().isBlank()) {
                sb.append("　 摘要：").append(truncate(r.content().strip())).append("\n");
            }
        }
        return sb.toString();
    }

    /** 去近似重复：按域名分组，只保留每组 score 最高的一条，并按 score 降序排列。 */
    private List<TavilyResult> dedup(List<TavilyResult> results) {
        return results.stream()
                .sorted(Comparator.comparing((TavilyResult r) -> r.score() == null ? 0.0 : r.score()).reversed())
                .collect(Collectors.toMap(
                        this::hostOf,
                        r -> r,
                        (existing, replacement) -> existing,   // 已按 score 降序，先到者即最高分
                        LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    /** 取 URL 的域名作为去重键；异常时回退到原始 URL，保证 key 非空。 */
    private String hostOf(TavilyResult r) {
        String url = r.url();
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        }
        catch (Exception e) {
            return url;
        }
    }

    /** 截断到 {@link #MAX_SNIPPET_LENGTH} 字：按 Unicode 码点计数（避免切半个 emoji/生僻字），并尽量在句末标点处断开。 */
    private String truncate(String s) {
        if (s.codePointCount(0, s.length()) <= MAX_SNIPPET_LENGTH) {
            return s;
        }
        // 第 MAX_SNIPPET_LENGTH 个码点之后的下标（码点安全，不会落在代理对中间）
        int end = s.offsetByCodePoints(0, MAX_SNIPPET_LENGTH);
        int cut = lastSentenceBoundary(s, end);
        return s.substring(0, cut > 0 ? cut : end) + "…";
    }

    /** 在 [0, limit) 内向左找最近的句末/分句标点，返回其后的截断点；找不到返回 -1。 */
    private int lastSentenceBoundary(String s, int limit) {
        for (int i = limit - 1; i > 0; i--) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '，'
                    || c == '、' || c == '…' || c == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TavilyResponse(String query, String answer, List<TavilyResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TavilyResult(String title, String url, String content, Double score) {
    }
}