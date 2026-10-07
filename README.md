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

### 1.3 什么是 Function Calling？

LLM 本身只能生成文本，无法获取实时信息（天气、汇率、数据库内容）。Function Calling 让 LLM 能"决定调用哪个函数、传什么参数"，但**实际执行由你的 Java 代码完成**。

- LLM 负责**决策**
- 你的方法负责**执行**

**没有 Function Calling：**

### 1.3 Tool的注意事項？

工具能抛异常吗	 能，但不推荐；内部 catch 返回友好文本更可控
返回格式有要求吗 推荐 String，简洁、接近自然语言；避免原始 JSON
工具重名会怎样	 会冲突，可能报错或覆盖；方法名不同或用 name 显式指定

### 遇到的问题

### 中文 key 在 Spring 配置里不可靠

`application.yml` 中的中文 key（如 `旅游顾问:`）在用 `Environment.getProperty()` 查询时，只有第一个能命中，其他返回 `null`。

**原因**：Spring 的 PropertySource 底层是 String 匹配，中文涉及 UTF-8 编解码，不同配置源处理不一致。

**解决**：配置 key 一律用英文（`travel-advisor`、`food-recommender`），中文只放在 value 里。