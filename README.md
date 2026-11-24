# Travel Helper · 旅行助手后端

一个基于 **Spring Boot 3 + Spring AI** 的智能旅行助手后端，接入大语言模型（DeepSeek），提供行程规划、美食推荐、翻译等能力。

---

## 1. 项目简介

Travel Helper 是一个智能旅行助手后端服务。它通过 Spring AI 的 `ChatClient` 接入大语言模型，采用「**角色 system prompt 切换**」的设计——用户通过 `role` 参数切换不同的助手身份，同时把**用户输入与偏好当作“数据”注入固定模板**，避免不可信文本污染 system prompt（防 prompt 注入）。

**核心特点**

- **多角色**：内置 `travel-advisor`（旅游规划）、`food-guide`（美食推荐）、`translator`（翻译），角色提示词在 `application.yml` 中集中配置，可自由扩展。
- **双模式输出**：同步一次性回复（`.call()`）与流式逐块输出（`.stream()` + SSE）两种接口。
- **安全边界**：system prompt 只来自受信任的配置文件；用户消息与偏好仅作为数据变量注入。

---

## 2. 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.5.15 |
| AI 框架 | Spring AI | 1.1.8 |
| 模型接入 | spring-ai-starter-model-openai（兼容 OpenAI 协议） | 1.1.8 |
| 大模型 | DeepSeek（火山方舟 OpenAI 兼容端点） | - |
| 构建工具 | Maven | 3.9+ |
| 测试 | JUnit 5 + Mockito + Spring Test + MockMvc + AssertJ | - |

---

## 3. 架构图

### 组件架构

```mermaid
flowchart LR
    subgraph Client[客户端]
        FE[前端 / curl / 浏览器]
    end

    subgraph Backend[Spring Boot 后端]
        Ctrl[ChatController<br/>GET /api/chat<br/>GET /api/chat/stream]
        CC[Spring AI ChatClient]
        CM[ChatModel<br/>OpenAiChatModel]
        Props[ChatProperties<br/>roles 配置]
    end

    LLM[(DeepSeek 大模型<br/>火山方舟 OpenAI 兼容接口)]

    FE -->|HTTP GET| Ctrl
    Props -->|role -> system prompt| Ctrl
    Ctrl -->|组装 Prompt, call / stream| CC
    CC -->|ChatOptions + messages| CM
    CM -->|OpenAI 兼容协议| LLM
    LLM -->|ChatResponse| CM
    CM -->|content / Flux| CC
    CC -->|文本 / SSE| Ctrl
    Ctrl -->|text/plain / text/event-stream| FE
```

### 一次请求的时序

```mermaid
sequenceDiagram
    autonumber
    participant C as 客户端
    participant Ctrl as ChatController
    participant CC as ChatClient
    participant M as ChatModel
    participant L as DeepSeek

    C->>Ctrl: GET /api/chat?message=&role=&prefs=
    Ctrl->>Ctrl: 根据 role 取 system prompt，用 PromptTemplate 组装 user 消息
    Ctrl->>CC: prompt().user(...).call().content()
    CC->>M: call(Prompt)
    M->>L: OpenAI 兼容 HTTP 请求
    L-->>M: ChatResponse
    M-->>CC: ChatResponse
    CC-->>Ctrl: 文本内容
    Ctrl-->>C: 200 text/plain
```

---

## 4. 本地运行步骤

### 前置条件

- **JDK 21**（必须；Spring Boot 3.5 / Java 文本块等语法要求 21，JDK 8 会编译失败）
- Maven 3.9+
- 一个可用的火山方舟 / DeepSeek API Key

> **注意**：如果在 IDEA 中构建时报「`unclosed string literal`」「`source 8 不支持文本块`」之类的错误，通常是 Maven Runner 用了 JDK 8。请在
> `Settings → Build → Build Tools → Maven → Runner` 选择 JDK 21，并确认 `Project SDK` 也是 21。

### 步骤

```bash
# 1. 设置环境变量（Windows PowerShell）
$env:ARK_API_KEY = "你的火山方舟 API Key"
$env:TAVILY_API_KEY = "你的 Tavily API Key"

# macOS / Linux
export ARK_API_KEY="你的火山方舟 API Key"
export TAVILY_API_KEY="你的 Tavily API Key"

# 2. 启动
mvn spring-boot:run

# 3. 验证
curl "http://localhost:8080/api/chat?message=你好"
```

启动成功后默认端口 **8080**。

### 数据库：默认 H2，可选连 Neon（PostgreSQL）

已接入 JDBC + HikariCP。默认使用**本地 H2 文件库**（`./data/travelhelper`，跨重启保留），**无需任何环境变量**，离线也能跑开发与测试；数据库文件在 `data/` 目录，已加入 `.gitignore`。

多轮对话记忆由 `JdbcChatMemoryRepository` 落库到 `SPRING_AI_CHAT_MEMORY` 表（外层 `MessageWindowChatMemory` 做滑动窗口）：H2 文件库与 Neon 都跨重启保留（详见 §7 多轮对话）。

想连 Neon（在家 / 有外网时），激活 `neon` profile 并设置 `DATABASE_URL`（`application-neon.yml` 会自动补 `jdbc:` 前缀）：

```bash
# PowerShell
$env:DATABASE_URL = "postgresql://user:password@host/neondb?sslmode=require"
mvn spring-boot:run -Dspring-boot.run.profiles=neon

# macOS / Linux
# export DATABASE_URL='postgresql://user:password@host/neondb?sslmode=require'
# SPRING_PROFILES_ACTIVE=neon mvn spring-boot:run
```

相关冒烟测试：`DatasourceSmokeTest` 验证 H2（始终绿）；`NeonSmokeTest` 仅在设置了 `DATABASE_URL` 时真正连 Neon，否则自动跳过。

---

## 5. 环境变量说明

| 变量名 | 说明 | 是否必填 | 示例 |
|--------|------|:--------:|------|
| `ARK_API_KEY` | 火山方舟（Ark）API Key | ✅ 必填 | `xxxxxxxx-xxxx-xxxx` |
| `TAVILY_API_KEY` | Tavily 联网搜索 API Key | ✅ 必填（联网搜索功能） | `tvly-xxxxxx` |
| `DATABASE_URL` | Neon（PostgreSQL）连接串，仅连 Neon 时用 | ❌ 可选 | `postgresql://user:pass@host/neondb?sslmode=require` |

### 模型相关配置（在 `application.yml` 中，非环境变量）

| 配置项 | 当前值 | 说明 |
|--------|--------|------|
| `spring.ai.openai.base-url` | `https://ark.cn-beijing.volces.com/api/v3` | 火山方舟 OpenAI 兼容端点；Coding Plan 需换成 `/api/coding/v3` |
| `spring.ai.openai.chat.completions-path` | `/chat/completions` | 覆盖默认 `/v1/chat/completions`，火山方舟正确路径 |
| `spring.ai.openai.chat.options.model` | `deepseek-v4-1-flash-260910` | 模型名 |
| `server.port` | `8080` | 服务端口 |
| `travel-helper.memory.max-messages` | `20` | 多轮记忆：每个会话保留的最大消息条数（滑动窗口大小） |
| `travel-helper.memory.expire-days` | `7` | 会话过期天数：超过 N 天没活动会被每天凌晨的定时任务清理 |

---

## 6. API 文档

### 6.1 同步对话 `GET /api/chat`

一次性返回 AI 的完整文本回复。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `message` | string | ✅ | 用户问题 |
| `role` | string | ❌ | 角色：`travel-advisor` / `food-guide` / `translator`；不传则不带 system prompt |
| `prefs` | string | ❌ | 用户偏好（出发地、天数、预算、人数、兴趣等），作为数据注入模板 |
| `conversationId` | string | ❌ | 多轮记忆会话 ID：同一 ID 共享历史上下文、不同 ID 隔离；不传则后端自动生成 UUID 并在响应中返回 |

**响应**：`200`，`Content-Type: application/json`，返回 `conversationId`（会话 ID，不传时后端自动生成）与 `content`（AI 回复文本）：

```json
{"conversationId": "6b8e...-uuid", "content": "AI 回复文本"}
```

**示例**

```bash
# 不带角色
curl "http://localhost:8080/api/chat?message=推荐一个成都3日游行程"

# 带角色 + 偏好
curl "http://localhost:8080/api/chat?message=帮我规划行程&role=travel-advisor&prefs=出发地北京,3天,预算2000,2人,喜欢美食"
```

多轮对话示例（同一 `conversationId` 共享上下文）：

```bash
curl "http://localhost:8080/api/chat?message=我想去东京玩3天&role=travel-advisor&conversationId=user-1"
curl "http://localhost:8080/api/chat?message=那第二天怎么安排&conversationId=user-1"
```

### 6.2 流式对话 `GET /api/chat/stream`

以 SSE（Server-Sent Events）逐块推送回复。

**请求参数**：与 `/api/chat` 完全一致。

**响应**：`Content-Type: text/event-stream`，逐块返回 AI 回复。

**示例**

```bash
curl -N "http://localhost:8080/api/chat/stream?message=介绍大阪3天行程&role=travel-advisor"
```

### 错误情况

| 状态码 | 说明 |
|:------:|------|
| 400 | `role` 不存在（`未知的角色: xxx`） |
| 500 | 模型调用异常 / 未配置 API Key 等 |

---

## 7. 多轮对话（记忆）

多轮对话让模型「记住上文」。本节说明本项目记忆系统的实现机制、会话 ID 设计、持久化选型、上下文裁剪策略、过期清理与测试方法。

### 7.1 Spring AI 的 ChatMemory 机制

Spring AI 用两层抽象组织「聊天记忆」：

| 接口 / 类 | 职责 |
|---|---|
| `ChatMemory` | 面向使用方的高层接口：`add(conversationId, Message)`、`add(conversationId, List<Message>)`、`get(conversationId)`、`clear(conversationId)` |
| `ChatMemoryRepository` | 面向存储的底层接口：`findConversationIds()`、`findByConversationId()`、`saveAll()`、`deleteByConversationId()` |
| `MessageWindowChatMemory` | `ChatMemory` 的实现类，包装一个 repository 并施加「最近 N 条」滑动窗口 |
| `MessageChatMemoryAdvisor` | Advisor 拦截器，在模型调用前后读写记忆 |

一次请求内部流转（由 `MessageChatMemoryAdvisor` 驱动）：

1. `before()`：按 `conversationId` 取出历史 → 合并进 prompt（`SystemMessage` 提到最前）→ 把本轮 user 消息写入记忆；
2. 调用模型生成回复；
3. `after()`：把 assistant 回复写入记忆。

因此**每完成一轮对话，持久化 1 条 user + 1 条 assistant = 2 条消息**。

本项目装配（`ChatMemoryConfig`）：JDBC 的 `ChatMemoryRepository` 由 Spring AI 自动配置提供，我们只需把它包进 `MessageWindowChatMemory`：

```java
@Bean
ChatMemory chatMemory(ChatMemoryProperties properties, ChatMemoryRepository repo) {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repo)
            .maxMessages(properties.getMaxMessages())
            .build();
}
```

`ChatController` 里注册 `MessageChatMemoryAdvisor.builder(chatMemory).build()`，并在每次请求时通过 `ChatMemory.CONVERSATION_ID` 参数把会话 ID 传给 advisor。

### 7.2 conversationId 设计

`conversationId` 是记忆的「分片键」，规则很简单：

- **同一 ID = 共享上下文**：多轮请求带相同 ID，模型能看到之前聊过的内容；
- **不同 ID = 完全隔离**：会话间互不串扰；
- **不传 = 后端自动生成**：生成一个 UUID，在响应体 `conversationId` 字段返回；客户端应保存并在后续请求原样回传，才能续上对话。

设计要点：

- ID 是**普通字符串**，粒度由客户端决定——一个用户一个 ID、或一个话题一个 ID 均可；
- 传参走 `ChatMemory.CONVERSATION_ID` 这个 advisor 参数，`ChatController` 原样透传；
- 滑动窗口与过期清理都以 `conversationId` 为单位：每个会话各自维护窗口、各自计时过期。

```bash
# 第一轮：拿到返回里的 conversationId
curl 'http://localhost:8080/api/chat?message=推荐成都3日游'

# 第二轮：原样回传，续上上下文
curl 'http://localhost:8080/api/chat?message=第2天想去熊猫基地&conversationId=<上一步的id>'
```

### 7.3 持久化方案对比

Spring AI 内置两种 `ChatMemoryRepository`：

| 方案 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| `InMemoryChatMemoryRepository` | 零依赖、快、无需配置 | 进程重启即丢，多实例不共享 | 演示、单元测试 |
| `JdbcChatMemoryRepository` | 落库、跨重启保留、按方言适配 H2 / PostgreSQL 等 | 需要数据源与建表 | **本项目选择** |

本项目用 JDBC：默认 **H2 文件库** `jdbc:h2:file:./data/travelhelper`（数据在 `data/`，重启不丢、离线可跑）；上生产连 **Neon/PostgreSQL** 时切 `neon` profile + `DATABASE_URL`。表为 Spring AI 标准表 `SPRING_AI_CHAT_MEMORY`：

| 列 | 说明 |
|---|---|
| `conversation_id` | 会话 ID |
| `content` | 消息内容 |
| `type` | `USER` / `ASSISTANT` / `SYSTEM` / `TOOL` |
| `timestamp` | 写入时间 |

建表由 `spring.ai.chat.memory.repository.jdbc.initialize-schema=always` 自动初始化。之所以从默认的 `embedded` 改成 `always`，是因为 `jdbc:h2:file:` 在运行时不被视为 embedded，默认模式会静默跳过建表、导致「表不存在」；H2 建表脚本非幂等，但 Spring AI 已内置 `continue-on-error=true`，重复启动时「已存在」的报错会被吞掉，跨重启不丢数据。

> 若要用 Redis / MongoDB 等：Spring AI 1.1.x 未内置对应的 `ChatMemoryRepository`，需自行实现该接口。

### 7.4 滑动窗口 vs 摘要压缩（上下文裁剪策略）

多轮对话的上下文会无限增长、最终爆 token，必须裁剪。常见策略对比：

| 策略 | 思路 | 优点 | 缺点 | 复杂度 |
|---|---|---|---|---|
| **滑动窗口** | 只保留最近 N 条 | 简单、成本确定、token 严格有界 | 最旧的消息（甚至关键早期信息，如目的地、人名）会丢 | 低 |
| 摘要压缩 | 用 LLM 把旧消息压成摘要 | 保留语义、可读性强 | 需额外 LLM 调用（成本+延迟），可能引入摘要误差、丢细节 | 中 |
| 向量检索 | 只召回与当前问题相关的历史 | 精准、历史可长期保存 | 需向量库 + embedding，实现复杂，可能漏掉跨话题依赖 | 高 |
| 混合策略 | 组合上述（滑窗 + 检索 / 滑窗 + 摘要） | 各取所长、效果最好 | 最复杂、调参多 | 很高 |

**本项目选择「滑动窗口」**：`MessageWindowChatMemory` 开箱即用、token 严格有界、零额外模型调用，契合当前单用户 / 短会话场景。窗口大小通过 `travel-helper.memory.max-messages`（默认 20）配置，**按会话粒度生效**。

若未来要保留更长历史又不爆 token，可在滑窗之上叠加摘要压缩或向量检索——`ChatMemory` 接口留有扩展空间，替换或包装现有实现即可。

### 7.5 过期清理策略

长期不活跃的会话会定时清理，避免记忆表无限膨胀（见 `ChatMemoryCleanupService`）。

- **触发**：每天凌晨 2 点，`@Scheduled(cron = "0 0 2 * * *")`（入口类已加 `@EnableScheduling`）；
- **判定**：取该会话 `timestamp`（最后一条消息）的最大值，早于 `now - expireDays` 天即判定过期并删除；
- **配置**：`travel-helper.memory.expire-days`（默认 7）；
- **删前日志**：每删一个会话前先 `log.info` 记录 `conversationId / 最后活动时间 / 消息条数`，删完再汇总；
- **跨库兼容**：`timestamp` 是 PostgreSQL 保留字、H2 下是普通列名，服务内部按方言解析列名，切换 Neon 无需改动。

### 7.6 记忆系统测试方法

记忆的正确性体现在「上文是否被带进下一次请求、会话是否隔离、重启是否保留、超窗是否遗忘、过期是否清理」。相关测试均 mock 掉真实 `ChatModel`，不发起外部调用：

| 场景 | 验证点 | 测试类 |
|---|---|---|
| 单轮对话 | 无跨轮依赖，首个请求正常返回 | `ChatControllerTest` |
| 多轮对话（依赖上文） | 第二轮 Prompt 里包含第一轮历史 | `ChatMemoryExampleTest.sameConversationIdSharesContext` |
| 会话隔离 | 不同 `conversationId` 的上下文互不可见 | `ChatMemoryExampleTest.differentConversationIdsAreIsolated` |
| 持久化（重启恢复） | 重建 DataSource 后消息仍能读回 | `ChatMemoryPersistenceTest` |
| 滑动窗口 | 超窗丢最旧、恰好满窗全保留 | `MessageWindowChatMemoryTest` |
| 过期清理 | 只删超期、删前有日志、天数可配 | `ChatMemoryCleanupServiceTest` |

> 并发（多会话同时对话）目前尚未实现专项测试，可后续用多线程 + 不同 `conversationId` 并发压测补充。

---

## 8. 已实现功能 / 待实现

### 已实现 ✅

- 多角色 system prompt 切换（`travel-advisor` / `food-guide` / `translator`），提示词配置化
- 同步一次性对话接口 `GET /api/chat`
- 流式 SSE 对话接口 `GET /api/chat/stream`
- 用户消息与偏好作为「数据」注入 `PromptTemplate`，防 prompt 注入
- 联网搜索（Function Calling）：接入 Tavily，模型可自动查景点 / 攻略 / 实时信息
- 多轮对话记忆（`ChatMemory` + `MessageChatMemoryAdvisor`，按 `conversationId` 共享 / 隔离上下文；`JdbcChatMemoryRepository` 落库，H2 / Neon 持久化；`MessageWindowChatMemory` 滑动窗口 + 会话过期定时清理，详见 §7）
- 单元测试（`@MockitoBean` mock 掉 `ChatModel`，测试接口非空 + 工具注册，无需真实调用大模型）
- 记忆系统测试（会话共享 / 隔离 / 持久化 / 滑动窗口 / 过期清理，见 §7.6）
- 用户偏好持久化（JPA + H2：主表 + 兴趣/忌口子表；PATCH 合并更新；聊天自动注入已保存偏好，见 §10）

### 待实现 🚧

- 更多专业工具：天气、汇率、机票/酒店等结构化数据 API（如 `OpenWeatherMap`、`Amadeus`）
- 结构化输出（`beanOutput` / JSON 实体）
- 认证鉴权（API Key / JWT）
- 历史行程持久化（数据库）
- 前端界面
- Docker 化部署 / CI
- 更完整的集成测试（用 WireMock 挡 OpenAI 协议模拟两轮工具调用）
- 并发对话（多会话同时对话）的专项测试

---

## 9. Spring AI 工具调用实战
### 9.1 工具调用原理

**一句话**：LLM 只能生成文本；「工具调用」本质是模型输出一段结构化的「我想调函数 X，参数是 Y」，**真正执行 X 的是我们自己的 Java 代码**，执行完再把结果塞回给模型，让它生成最终答复。

在 Spring AI 里：

1. 用 `@Tool` 标注方法、`@ToolParam` 标注参数。方法名就是工具名（也可用 `name` 覆盖），工具名 + 描述 + 参数 schema 会序列化进请求的 `tools` 字段发给模型。
2. 用 `ChatClient.builder().defaultTools(webSearchTools).build()` 注册工具。
3. 一轮「工具调用」的完整时序：

```mermaid
sequenceDiagram
    autonumber
    participant App as 你的代码
    participant CM as ChatModel(OpenAiChatModel)
    participant LLM as 大模型
    participant Tool as 你的 @Tool 方法

    App->>CM: call(Prompt 带 tools)
    CM->>LLM: POST /chat/completions（含 tools 定义）
    LLM-->>CM: assistant message 带 tool_calls[]
    CM->>Tool: 按 name 找到 ToolCallback 反射调用
    Tool-->>CM: 返回结果字符串
    CM->>LLM: 结果作为 tool 角色消息回填，再次请求
    LLM-->>CM: 最终文本回复
    CM-->>App: ChatResponse
```

**最容易踩的架构事实**：上面这个「执行循环」住在 **`ChatModel` 内部**（Spring AI 1.1.x 的 `OpenAiChatModel` 持有 `ToolCallingManager`），**而不是 `ChatClient`**。所以：

- `ChatClient` 上做的只是「组装 Prompt + 注册工具」；
- 真正「收 tool_calls → 执行 → 回填 → 再收最终答复」是模型的行为；
- 单测里 **mock `ChatModel` 只能断言「工具已注册并随 Prompt 传给模型」，测不了两轮执行**（本仓库 `toolCallingIsTriggered`、`travelQuestionReturnsAnswerAndRegistersSearchTool` 用的就是这个边界）。

另外两点：

- **模型可并行调用多个工具**：同一轮 assistant message 里可以有多个 `tool_calls`，模型会「多拿证据」而不是严格二选一。
- `ToolCallingChatOptions.isInternalToolExecutionEnabled()` 默认 `true`；关掉后 ChatClient 不再自动执行，需要你手动调用 `.toolExecution()`。

### 9.2 工具描述怎么写

工具名 + `description` + 参数 `description` 是模型「要不要调、怎么调」的**唯一依据**（模型看不到你的方法体）。写描述就是写「给模型的说明书」。

**原则**：

1. **写清楚「什么时候该用」**：触发条件 + 能力边界，而不是只说「这是一个搜索」。
2. **参数描述给示例和格式**：建议语言、取值范围、示例值都要有。
3. **避免职责重叠**：多个工具能力撞车会让模型困惑、两边都调、多烧配额。
4. **返回对 LLM 友好的文本**：`String`、接近自然语言，别丢原始 JSON 或整页正文。

对比：

| | 差 | 好 |
|---|---|---|
| description | 「搜索」 | 「联网搜索景点、攻略、实时信息等外部知识；返回相关网页的标题、摘要和链接」 |
| 参数 | `keyword`（无描述） | 「搜索关键词，建议使用中文」 |

**反面教材**：本仓库做过的 `searchRestaurant` 假工具，命名/描述和通用 `search` 重叠（「搜索当地餐厅、美食」），结果模型面对「推荐餐厅」**两个都调了**。这本身就是反模式——与其按领域堆一堆搜索工具让模型挑，不如一个通用 `search`，把领域意图写进 query（如 `search("成都 火锅店 推荐")`）。

### 9.3 容错策略

**总原则**：

- 工具方法**永不抛异常**，永远 `catch` 返回可读文本（异常会打断 tool-calling 循环，同步路径直接 500）；
- 重试要克制（按次计费的 API，每次重试都在烧额度）；
- 区分「瞬时错误 / 确定性错误 / 空结果」。

| 场景 | 重试？ | 降级 | 返回给 LLM |
|------|--------|------|-----------|
| 网络超时 | 重试 1 次，退避 | 备用源 / 放弃 | 「联网搜索超时，暂无法获取实时信息」 |
| 429 限流 | 读 `Retry-After`，指数退避 1~2 次 | 备用源 / 缓存 | 「搜索服务限流，请稍后重试」 |
| 5xx | 重试 1 次，退避 | 备用源 / 放弃 | 「搜索服务故障，无法获取结果」 |
| 空结果 | **不机械重试**，改写 query 再试 1 次 | 换更宽泛词 | 「未找到相关内容」 |
| 参数非法 | **不重试**（确定性错误） | 入口校验/兜底 | 「参数不合法，请提供有效关键词」 |

**本项目落地**（`WebSearchTools` + `TavilyConfig`）：

- **5s 超时**：只作用在 Tavily 这个 `RestClient` 上（独立 `@Bean`），不污染 LLM 客户端——否则同步对话会被限时 500；
- **失败重试 2 次、间隔 1s**；
- **空结果**返回「未找到与「xx」相关内容。」；
- **最终失败**返回友好兜底文案，让模型如实说明而非编造；
- **`log.warn` 记录每次失败原因**。

何时上框架：只要重试 → Spring Retry / 手写循环即可；要「重试 + 熔断 + 限流 + 监控」成套、防重试风暴 → Resilience4j。

### 9.4 排查「工具没被调用」

按从易到难：

1. **注册了吗？** 类加了 `@Component` 被扫描到、`defaultTools(...)` 传进去了吗；用的 `chatClient` 是不是注册工具的那个实例。
2. **描述是否太模糊/重叠？** 模型不知道该用哪个，可能干脆不用（或乱用）。
3. **模型支持 Function Calling 吗？** 换成不支持工具调用的模型/端点，模型只会聊天。
4. **内部执行开关被关了吗？** `isInternalToolExecutionEnabled == false` 时不会自动执行。
5. **问题真的需要工具吗？** 问「你好」不调工具是正常表现，不是 bug。
6. **看请求侧**：断点/日志看 `Prompt.getOptions()`（`ToolCallingChatOptions.getToolCallbacks()`）里有没有这个工具；没有 → 注册问题，有 → 模型没选它，回到第 2 点。
7. **看执行侧**：在 `@Tool` 方法入口打断点 + `log`，确认是否真的被反射调用（这步在 mock 模型下不可达，见 9.1）。
8. **要看完整两轮执行**：用 WireMock 挡住 OpenAI 协议，返回「先 tool_calls、后最终回复」两轮响应。
9. **模型「宁可编也不搜」**：靠 system prompt 铁律约束（本仓库已在 `travel-advisor` 里加了「实时信息必须先调搜索、搜不到就如实说明」）。

---

## 10. 用户偏好（JPA + H2）

目标：把「预算、节奏、同行人、忌口、兴趣、出发地」记在库里，同一用户换会话仍可用。

### 10.1 表结构

| 表 | 说明 |
|---|---|
| `user_preference` | 一个用户一行，唯一键 `user_id` |
| `user_preference_interest` | 兴趣（多值），主键 `(preference_id, interest)` |
| `user_preference_dietary` | 忌口（多值），主键 `(preference_id, dietary)` |

单值字段：`departure_city`、`pace`、`companion_type`、`adult_count`、`child_count`、`budget_level`、`budget_note`、`last_conversation_id`。

枚举以 `VARCHAR` 存字符串（自动带 `check` 约束），不用 ORDINAL；空值统一为 `NULL`，空字符串不入库。

### 10.2 为什么键是 user_id 而不是 conversationId

`conversationId` 是一次会话的标识，新开对话就变；用它当键会导致「换会话读不到偏好」，还会被 7 天记忆清理任务连带删除。`last_conversation_id` 只用于溯源。

身份来源（`UserIdentityResolver`）：

1. 认证主体 `Principal`（接入 JWT 后即 `sub`）；
2. `userId` 参数（仅 `travel-helper.auth.allow-user-id-param=true` 时，本地模拟多用户）；
3. `travel-helper.auth.dev-user-id`（本地兜底）。

上线时把 `dev-user-id` 置空、`allow-user-id-param` 设为 `false`。

### 10.3 更新语义：合并，不覆盖

`PATCH /api/preferences` 只写请求里出现的字段：

| 输入 | 行为 |
|---|---|
| 标量 `null` | 不改（用户这轮没提） |
| 集合 `null` | 不改 |
| 集合 `[]` | 清空 |
| 集合 `[a,b]` | 整体替换 |
| `clearFields: ["budget"]` | 清空标量 |

### 10.4 接口

```powershell
# 读取（没有记录返回空视图）
curl.exe "http://localhost:8080/api/preferences"

# 合并更新
curl.exe -X PATCH "http://localhost:8080/api/preferences?conversationId=session-A" `
  -H "Content-Type: application/json" `
  -d '{"departureCity":"北京","pace":"RELAXED","budgetLevel":"COMFORT","interests":["美食"],"dietary":["不吃辣"]}'

# 清空
curl.exe -X DELETE "http://localhost:8080/api/preferences"
```

聊天接口新增可选 `userId` 参数；保存的偏好会以「数据」形式注入 user 消息，保持在 system prompt 之外。本次请求的 `prefs` 优先于已保存偏好。

### 10.5 配置与建表

- `spring-boot-starter-data-jpa` + 默认 H2 文件库；
- `spring.jpa.hibernate.ddl-auto=update`（开发期）；生产建议 `validate` + Flyway；
- `spring.jpa.open-in-view=false`；
- 偏好表由 Hibernate 建；Spring AI 的 `SPRING_AI_CHAT_MEMORY` 仍由它自己的 `initialize-schema` 管理。

### 10.6 测试

| 场景 | 测试类 |
|---|---|
| 合并不抹掉未提字段 / `[]` 清空 / `clearFields` / 跨会话 / 用户隔离 / 版本递增 | `UserPreferenceServiceTest` |
| 只输出已知字段、集合稳定排序 | `PreferencePromptRendererTest` |
| 接口 GET/PATCH/DELETE + 聊天 Prompt 注入 | `UserPreferenceIntegrationTest` |

---

## 附录：开发笔记 / Notes & Lessons Learned

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

#### 1.3 什么是 Function Calling？

LLM 本身只能生成文本，无法获取实时信息（天气、汇率、数据库内容）。Function Calling 让 LLM 能「决定调用哪个函数、传什么参数」，但**实际执行由你的 Java 代码完成**。

- LLM 负责**决策**
- 你的方法负责**执行**

#### 1.4 Tool 的注意事项

| 问题 | 结论 |
|------|------|
| 工具能抛异常吗 | 能，但不推荐；内部 catch 返回友好文本更可控 |
| 返回格式有要求吗 | 推荐 String，简洁、接近自然语言；避免原始 JSON |
| 工具重名会怎样 | 会冲突，可能报错或覆盖；方法名不同或用 `name` 显式指定 |

#### 1.5 Search API

- 选了：**Tavily**
- 免费额度：**1000 credit/月**（basic 搜索 1 credit/次、advanced 2 credit/次），**无需信用卡**
- 返回结构关键字段：`results[].title / url / content`（核心，喂给 LLM）；`score`（排序 + 去重）；`answer`（可选，本次未用）；`raw_content`（整页正文，默认丢弃）
- 踩坑：
  - `raw_content` 是 token 黑洞 → `include_raw_content=false`，只取 `content` 摘要
  - `query` 与 `language` 语言要一致（中文查询 + `language: zh`），召回才准
  - 同域名结果会重复 → 按域名去重、只留 `score` 最高的一条
  - 中文通用内容可用，但大陆长尾（门票/开放时间/本地新闻）召回偏弱 → 必要时用百度（SerpAPI `engine=baidu`）兜底

### 今日小结（2026-10-08）

- 修复 ChatClient 单元测试：工具执行循环在 **ChatModel 内部**（`OpenAiChatModel` 持有 `ToolCallingManager`）而非 ChatClient，mock 模型无法模拟两轮回路 → 改为断言「工具已注册并随 Prompt 传给模型」
- 完善 README：项目简介 / 技术栈 / 架构图 / 运行 / 环境变量 / API / 功能清单
- 接入 Tavily 联网搜索：`config/TavilyProperties` + `tools/WebSearchTools`，注册进 `ChatController` 的 ChatClient
- `WebSearchTools.format()` 优化：按域名去重 + 摘要按「码点安全 + 句末断句」截断（250 字）

### 遇到的问题

#### 中文 key 在 Spring 配置里不可靠

`application.yml` 中的中文 key（如 `旅游顾问:`）在用 `Environment.getProperty()` 查询时，只有第一个能命中，其他返回 `null`。

**原因**：Spring 的 PropertySource 底层是 String 匹配，中文涉及 UTF-8 编解码，不同配置源处理不一致。

**解决**：配置 key 一律用英文（`travel-advisor`、`food-guide`），中文只放在 value 里。

---