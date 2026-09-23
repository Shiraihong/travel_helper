## 开发笔记 / Notes & Lessons Learned

本项目在开发过程中遇到并解决了一些典型问题，记录如下。

---

### 1. Spring AI 核心概念

#### 1.1 `.call()` vs `.stream()`

| 维度 | `.call()` | `.stream()` |
|------|-----------|-------------|
| 返回类型 | `CallResponseSpec` | `StreamResponseSpec` |
| 最终结果 | `String` / 对象 | `Flux<String>` |
| 阻塞行为 | 同步阻塞，等完整结果 | 非阻塞，逐块返回 |
| 首字延迟 | 高 | 低 |
| 底层 HTTP | 普通 POST | `stream=true` + SSE |
| 适用场景 | 短回复、后台任务 | 长回复、打字机效果 |

**注意**：调用 `.call()` 或 `.stream()` 本身不触发模型执行，真正的调用发生在 `.content()` / `.chatResponse()` / `.entity()` 时。

#### 1.2 `PromptTemplate` vs 拼字符串

**拼字符串的问题**：
- 可读性差，变量一多就乱
- 容易漏空格
- 无类型检查，变量名写错只有运行时才发现
- 无法复用

**用 `PromptTemplate`**：
```java
PromptTemplate template = new PromptTemplate("请用{language}回答：{question}");
Prompt prompt = template.create(Map.of(
    "language", "中文",
    "question", "什么是微服务？"
));
```

### 遇到的问题

### 中文 key 在 Spring 配置里不可靠

`application.yml` 中的中文 key（如 `旅游顾问:`）在用 `Environment.getProperty()` 查询时，只有第一个能命中，其他返回 `null`。

**原因**：Spring 的 PropertySource 底层是 String 匹配，中文涉及 UTF-8 编解码，不同配置源处理不一致。

**解决**：配置 key 一律用英文（`travel-advisor`、`food-recommender`），中文只放在 value 里。