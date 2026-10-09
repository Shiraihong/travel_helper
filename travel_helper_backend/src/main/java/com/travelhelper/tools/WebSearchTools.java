package com.travelhelper.tools;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.travelhelper.config.TavilyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
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

    /** 结果相关性阈值：低于此分数的结果视为低相关/垃圾内容丢弃。 */
    private static final double MIN_SCORE = 0.5;

    /** 首次失败后的额外重试次数（总共尝试 1 + MAX_RETRIES 次）。 */
    private static final int MAX_RETRIES = 2;

    /** 两次重试之间的等待时间。 */
    private static final long RETRY_INTERVAL_MS = 1000;

    /** 搜索彻底失败时回给 LLM 的兜底文案，让模型如实说明而非编造。 */
    private static final String FALLBACK_MESSAGE =
            "联网搜索暂时不可用（多次尝试后仍未成功），请基于已有知识回答，并明确告知用户未获取到最新实时信息。";

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    private final TavilyProperties properties;
    private final RestClient restClient;

    public WebSearchTools(TavilyProperties properties,
                          @Qualifier("tavilySearchRestClient") RestClient tavilyRestClient) {
        this.properties = properties;
        // 使用 TavilyConfig 单独配置的 5s 超时客户端（传输层独立，便于测试用 MockRestServiceServer 替换）
        this.restClient = tavilyRestClient;
    }

    @Tool(description = "联网搜索景点、攻略、实时信息等外部知识；返回相关网页的标题、摘要和链接")
    public String search(@ToolParam(description = "搜索关键词，建议使用中文") String query) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return "未配置 TAVILY_API_KEY，无法联网搜索。";
        }

        TavilyResponse response = searchWithRetry(query);
        return response == null ? FALLBACK_MESSAGE : format(response, query);
    }

    /** 带重试的搜索：失败最多额外重试 {@link #MAX_RETRIES} 次，每次间隔 {@link #RETRY_INTERVAL_MS}ms；仍失败返回 null。 */
    private TavilyResponse searchWithRetry(String query) {
        Exception lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doSearch(query);
            }
            catch (Exception e) {
                lastError = e;
                log.warn("Tavily 搜索失败（第 {}/{} 次），query=\"{}\"，原因：{}",
                        attempt + 1, MAX_RETRIES + 1, query, e.toString());
                if (attempt < MAX_RETRIES) {
                    sleepQuietly(RETRY_INTERVAL_MS);
                }
            }
        }
        log.warn("Tavily 搜索重试 {} 次后仍失败，query=\"{}\"，最后失败原因：{}",
                MAX_RETRIES, query, lastError == null ? "未知" : lastError.toString());
        return null;
    }

    /** 单次 HTTP 调用；网络异常 / 非 2xx 会以异常形式抛出，交由 {@link #searchWithRetry(String)} 重试。 */
    private TavilyResponse doSearch(String query) {
        // 可选：需要时加 include_domains（白名单）/ exclude_domains（黑名单）进一步挡掉广告站、内容农场
        Map<String, Object> body = Map.of(
                "query", query,
                "search_depth", "basic",
                "max_results", 5,
                "language", "zh");

        return this.restClient.post()
                .uri(properties.getBaseUrl() + "/search")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TavilyResponse.class);
    }

    /** 忽略中断地等待指定毫秒；被中断时保留中断标志并返回。 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 把 Tavily 返回整理成 LLM 易读的文本：去重、截断、只保留核心字段。 */
    private String format(TavilyResponse response, String query) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "未找到与「" + query + "」相关内容。";
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

    /** 过滤低分结果 + 去近似重复：按域名分组保留 score 最高的一条，并按 score 降序排列。 */
    private List<TavilyResult> dedup(List<TavilyResult> results) {
        return results.stream()
                .filter(r -> r.score() == null || r.score() >= MIN_SCORE)   // 丢低相关/垃圾结果
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