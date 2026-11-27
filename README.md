# GameSage

基于 **Spring Boot + Spring AI + DeepSeek** 的《第五人格》「加页手记」玩法专属攻略助手。项目使用本地 Markdown 攻略构建向量知识库（pgvector），通过 RAG（检索增强生成）为玩家提供职业推荐、怪物机制、辞章价值等问答服务，并支持流式输出、多轮会话记忆与安全风控。

> 配套文档：[`GameSage 测试用例.md`](./GameSage%20测试用例.md)

---

## 功能特性

- **本地知识库 RAG**：启动时将 `resources/guides` 下的 Markdown 攻略按标题切分、Token 二次切分后写入 pgvector，问答时通过工具调用检索相关片段。
- **流式回答**：基于 WebFlux + SSE 逐字返回，前端页面实时渲染。
- **多轮会话记忆**：按 `sessionId` 隔离上下文，超过 5 个 turn 自动压缩（保留最近 5 轮）。
- **安全风控**：`GameSageRiskAdvisor` 在调用模型前拦截「代练 / 外挂 / 脚本 / 挂机刷取」等风险请求，直接返回合规拒答。
- **范围约束**：系统提示词限定只回答「加页手记」相关内容，拒绝排位、其他游戏及无关请求，并禁止编造数值。
- **降级可用**：Embedding 或向量库异常时应用仍可启动，检索失败会返回明确提示而非 500。
- **内置前端**：`static/index.html` 提供开箱即用的聊天页面。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 语言 / 构建 | Java 17、Maven |
| 框架 | Spring Boot 4.1.3、Spring WebFlux |
| AI | Spring AI 2.1.0（ChatClient、Advisor、Tool Calling、Session） |
| 对话模型 | DeepSeek（`deepseek-flash`，OpenAI 兼容协议） |
| Embedding | 硅基流动 `BAAI/bge-m3`（1024 维） |
| 向量数据库 | PostgreSQL 16 + pgvector（HNSW / COSINE） |
| 其他 | Lombok、JUnit 5 + Mockito |

---

## 目录结构

```
GameSage
├── pom.xml                         # 父 POM：依赖版本、编译与打包插件
├── config/
│   └── docker-compose.yml          # pgvector 数据库编排
├── gameSage-code/                  # 业务模块
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/game/sage/
│       │   │   ├── GameSageApplication.java     # 启动类
│       │   │   ├── advisor/                     # 风控 Advisor
│       │   │   ├── config/                      # ChatClient / Session 配置
│       │   │   ├── controller/                  # /api/guide/ask 接口
│       │   │   ├── loader/                      # 知识库加载器（策略模式）
│       │   │   │   └── identityV/               # 职业 / 怪物 / 通用攻略
│       │   │   ├── model/                       # 文档元数据
│       │   │   ├── prompt/                      # 系统提示词
│       │   │   ├── tools/                       # 向量检索 Tool
│       │   │   └── utils/                       # Markdown 切分与通用常量
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── guides/IdentityV/*.md        # 攻略原始数据
│       │       └── static/index.html            # 内置聊天页面
│       └── test/                                # 单元测试（84 个用例）
└── test-case-resources/            # 测试用例截图
```

---

## 架构概览

```
浏览器 (static/index.html)
        │  GET /api/guide/ask?question=...&sessionId=...
        ▼
GameSageController ──► ChatClient
        │                 ├── GameSageRiskAdvisor（风险词拦截，最高优先级）
        │                 ├── SimpleLoggerAdvisor（请求/响应日志）
        │                 ├── SessionAdvisors（会话记忆 + 压缩）
        │                 └── Tool: IdentityVCrypticNotesTool
        │                          │ similaritySearch(topK=4, threshold=0.65)
        ▼                          ▼
   Flux<String> 流式返回      pgvector 知识库
```

启动阶段：`GuideDataLoader`（`CommandLineRunner`）在 `datasource.update=true` 时依次执行各 `IGuideData` 实现，把 Markdown 攻略切分并写入向量库。

---

## 环境要求

- JDK 17+
- Maven 3.6+
- Docker / Docker Compose（用于启动 pgvector）
- DeepSeek API Key（对话模型）
- 硅基流动 API Key（Embedding）

---

## 快速开始

### 1. 启动向量数据库

```bash
cd config
export SQL_PASSWORD=your_password        # Windows PowerShell: $env:SQL_PASSWORD="your_password"
docker compose up -d
```

该容器使用 `pgvector/pgvector:pg16` 镜像，映射 `5432` 端口，数据库名为 `GameSage`。首次启动时 Spring AI 会根据 `initialize-schema: true` 自动创建 `vector_store` 表。

### 2. 配置环境变量

| 变量 | 说明 |
|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek 对话模型 API Key |
| `SILICONFLOW_API_KEY` | 硅基流动 Embedding API Key |
| `SQL_PASSWORD` | PostgreSQL 密码（需与 docker-compose 一致） |

### 3. 构建与运行

```bash
# 项目根目录
mvn clean package

java -jar gameSage-code/target/gameSage-code-1.0-SNAPSHOT.jar
```

或开发模式直接运行：

```bash
mvn spring-boot:run -pl gameSage-code
```

启动后访问 <http://localhost:8080/index.html> 即可开始对话。

### 4. 首次加载知识库

`application.yml` 中默认 `datasource.update: false`，即启动时跳过知识库写入。首次使用或攻略内容更新后，将其改为 `true` 再启动一次：

```yaml
datasource.update: true
```

日志出现 `加页手记知识库加载完成，共写入 N 个片段` 即表示成功；加载完成后建议改回 `false`，避免每次启动重复调用 Embedding。

---

## 配置说明

核心配置位于 `gameSage-code/src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8080` | HTTP 端口 |
| `spring.ai.openai.api-key` | `${DEEPSEEK_API_KEY}` | 对话模型密钥 |
| `spring.ai.openai.base-url` | `https://api.deepseek.com` | 对话模型地址 |
| `spring.ai.openai.chat.model` | `deepseek-flash` | 对话模型 |
| `spring.ai.openai.embedding.base-url` | `https://api.siliconflow.cn/v1` | Embedding 服务地址 |
| `spring.ai.openai.embedding.model` | `BAAI/bge-m3` | Embedding 模型 |
| `spring.ai.vectorstore.pgvector.*` | HNSW / COSINE / 1024 维 | 向量索引配置 |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/GameSage` | 数据库连接 |
| `datasource.update` | `false` | 启动时是否重新加载知识库 |

---

## API 说明

### 问答（流式）

```
GET /api/guide/ask?question={问题}&sessionId={会话ID}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `question` | 是 | 用户问题，长度上限 50 字符，超长返回提示 |
| `sessionId` | 是 | 会话标识，相同 ID 共享多轮上下文 |

响应为 `text/html;charset=utf-8` 的流式文本（SSE 风格分块）。

```bash
curl -N "http://localhost:8080/api/guide/ask?question=新手适合玩哪个求生者&sessionId=sess_abc123"
```

> 前端每次刷新会生成新的 `sessionId`，因此刷新后不会保留上一轮上下文。

---

## 知识库扩展

1. 在 `gameSage-code/src/main/resources/guides/` 下新增 Markdown 攻略文件；
2. 实现 `IGuideData` 接口（参考 `loader/identityV/IdentityVMonstersGuideData`），声明文件路径与元数据：

```java
@Component
@RequiredArgsConstructor
public class MyGuideData implements IGuideData {

    private static final String[] GUIDE_FILES = {
            "classpath:guides/IdentityV/MyGuide.md"
    };

    private final ResourceLoader resourceLoader;

    @Override
    public List<Document> loadGuideData() throws IOException {
        DocumentsMetaData meta = new DocumentsMetaData(
                INFER_SOURCE_IdentityV_GUIDE, INFER_CATEGORY_CLASSICS, INFER_MODE_BOTH, INFER_GAME_IdentityV);
        return MarkdownFilesUtils.getDocuments(GUIDE_FILES, resourceLoader, meta);
    }
}
```

3. 设置 `datasource.update: true` 重启应用完成入库。

切分逻辑见 `MarkdownFilesUtils`：先按 Markdown 标题切块，再对超长块做 Token 二次切分并合并过短块；文档 ID 由内容摘要生成，便于幂等去重。

文档元数据包含 `source`、`file`、`category`（classics / character / monster）、`mode`（survivor / regulator / both）、`game`，检索工具据此做视角过滤。

---

## 测试

```bash
# 运行全部单元测试
mvn test

# 打包并跳过测试
mvn clean package -DskipTests
```

测试覆盖配置装配、风控 Advisor、知识库加载器、Markdown 切分、检索工具等模块。端到端功能与异常场景见 [`GameSage 测试用例.md`](./GameSage%20测试用例.md)。

---

## 常见问题

- **编译报「找不到符号」且类确实存在**：多为修改包名大小写后 `target/classes` 残留旧目录（Windows 文件系统不区分大小写）。执行 `mvn clean package` 或删除 `target` 后重试。
- **启动后检索一直失败**：检查 Embedding 的 `base-url`、`api-key`、`model` 及账户余额；应用会降级启动并在日志中给出原因。
- **回复没有命中本地攻略**：确认 `datasource.update` 已开启并完成入库，且问题中的游戏术语与攻略文本接近（相似度阈值 0.65）。
- **端口/数据库连接失败**：确认 pgvector 容器已启动、`SQL_PASSWORD` 与 `application.yml` 一致。

---

## 打包产物

`mvn clean package` 后在 `gameSage-code/target/` 生成：

| 文件 | 说明 |
|---|---|
| `gameSage-code-1.0-SNAPSHOT.jar` | Spring Boot 可执行 fat jar（`java -jar` 直接运行） |
| `gameSage-code-1.0-SNAPSHOT.jar.original` | repackage 前的原始 jar |
