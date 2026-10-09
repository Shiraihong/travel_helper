package com.travelhelper.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.travelhelper.config.TavilyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * WebSearchTools 单元测试：用 MockRestServiceServer 挡掉真实 HTTP，不调用外部 API。
 */
class WebSearchToolsTest {

    private static final String BASE_URL = "https://api.example.com";

    private MockRestServiceServer server;
    private WebSearchTools tools;

    @BeforeEach
    void setUp() {
        TavilyProperties properties = new TavilyProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(BASE_URL);

        // 把 mock 绑到 builder 上，再用它 build 出工具用的 RestClient，mock 即拦截所有请求
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.tools = new WebSearchTools(properties, builder.build());
    }

    @Test
    @DisplayName("正常搜索返回非空结果")
    void searchReturnsResultsOnSuccess() {
        String json = """
                {"query":"成都火锅","results":[
                    {"title":"老码头火锅","url":"https://example.com/a","content":"这是一家知名火锅店","score":0.9}
                ]}
                """;
        server.expect(requestTo(BASE_URL + "/search"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        String result = tools.search("成都火锅");

        assertThat(result).isNotBlank();
        assertThat(result).contains("老码头火锅");
        server.verify();
    }

    @Test
    @DisplayName("API 失败（500）时返回友好文本")
    void searchReturnsFallbackMessageOnServerError() {
        // 初次 + 两次重试，共三次请求都返回 500
        server.expect(requestTo(BASE_URL + "/search")).andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/search")).andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/search")).andRespond(withServerError());

        String result = tools.search("成都火锅");

        assertThat(result).contains("联网搜索暂时不可用");
        server.verify();
    }

    @Test
    @DisplayName("空结果时返回未找到相关内容")
    void searchReturnsEmptyMessageOnNoResults() {
        String json = """
                {"query":"不存在的地方","results":[]}
                """;
        server.expect(requestTo(BASE_URL + "/search"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        String result = tools.search("不存在的地方");

        assertThat(result).contains("未找到与「不存在的地方」相关内容");
        server.verify();
    }
}