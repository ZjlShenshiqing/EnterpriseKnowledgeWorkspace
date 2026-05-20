# Nacos 微服务治理集成设计

## 目标

引入 Nacos 实现服务发现与配置管理，消除当前架构中所有硬编码服务地址和分散配置的问题。

## 现状问题

1. **路由硬编码出错**：Gateway 路由 knowledge-ai → `localhost:8081`（实际 8083），workbench → `localhost:8083`（实际 8084）
2. **配置分散**：4 个服务各有一份 `application.yml`，MySQL / Redis / API Key 在多处分身
3. **无服务发现**：服务上下线需手动改路由重启 Gateway
4. **无负载均衡**：单实例，Gateway 直连固定地址

## 架构变更

```
改动前：Gateway ──http://localhost:xxxx──→ 各服务（硬编码）
改动后：Gateway ──lb://service-name────→ Nacos 解析实例列表 → 各服务
```

所有服务启动时向 Nacos 注册（服务名 + IP + 端口）。Gateway 通过 `lb://` 前缀触发 Spring Cloud LoadBalancer，从 Nacos 获取实例列表并做客户端负载均衡。

共享配置（DB、Redis、API Key 等）统一放在 Nacos，本地 `application.yml` 只保留 Nacos 连接信息和各服务专属配置。

## 版本对齐

```
Spring Boot 3.4.4 → Spring Cloud 2024.0.1 → Spring Cloud Alibaba 2024.0.0.3 → Nacos 2.5.1
```

## Nacos Server 部署

Docker standalone 模式：

```bash
docker run -d --name nacos-standalone -e MODE=standalone \
  -p 8848:8848 -p 9848:9848 nacos/nacos-server:v2.5.1
```

控制台：`http://localhost:8848/nacos`（nacos/nacos）

## 依赖变更

所有 4 个服务在各自 pom.xml 中新增：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>2024.0.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

Gateway 服务额外添加：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

Workbench 服务额外添加（使用 `@LoadBalanced RestTemplate`）：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

## 配置拆分

### Nacos 配置分层

三层结构：

| 层级 | Data ID | 内容 | 加载方式 |
|---|---|---|---|
| 服务专属 | `${spring.application.name}.yaml` | 数据库连接、服务端口等 | 自动加载 |
| 共享 | `common-config.yaml` | Redis、API Key、Milvus | `shared-configs` |
| 本地 | `application.yml` | Nacos 连接地址 | 本地文件 |

**共享配置**（Data ID: `common-config.yaml`, Group: `DEFAULT_GROUP`）：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
app:
  agent:
    llm:
      api-key: ${DEEPSEEK_API_KEY:}
      base-url: https://api.deepseek.com
    web-search:
      api-key: ${WEB_SEARCH_API_KEY:}
      base-url: https://api.bochaai.com/v1
  milvus:
    uri: http://localhost:19530
collab:
  service:
    url: http://collaboration-service
knowledge:
  service:
    url: http://knowledge-ai-service
```

**服务专属配置示例**（Data ID: `knowledge-ai-service.yaml`, 由 Nacos 根据 `spring.application.name` 自动匹配）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/enterprise_knowledge_ai
    username: root
    password: ${DB_PASSWORD:123456}
```

数据库配置拆分到各服务自己的 Data ID，因为每个服务使用不同的 schema（`enterprise_gateway` / `enterprise_knowledge_ai` / `enterprise_collaboration`）。

### 本地保留（application.yml）

```yaml
spring:
  application:
    name: knowledge-ai-service  # 各服务不同
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml
        fail-fast: false
        shared-configs:
          - data-id: common-config.yaml
            group: DEFAULT_GROUP
            refresh: true

server:
  port: 8083
app:
  kb:
    upload-dir: ./data/kb-uploads
```

不使用 `bootstrap.yml`（Spring Boot 3.4 已移除 bootstrap 支持）。

## 服务名约定

| 服务 | spring.application.name |
|---|---|
| Gateway | `gateway-service` |
| Knowledge-AI | `knowledge-ai-service` |
| Collaboration | `collaboration-service` |
| Workbench | `workbench-service` |

## Gateway 路由改造

```yaml
cloud:
  gateway:
    routes:
      - id: knowledge-ai
        uri: lb://knowledge-ai-service
        predicates:
          - Path=/api/kb/**,/api/ai-qa/**
      - id: collaboration
        uri: lb://collaboration-service
        predicates:
          - Path=/api/meetings/**,/api/todos/**,/api/tasks/**
      - id: workbench
        uri: lb://workbench-service
        predicates:
          - Path=/api/workbench/**
```

## Workbench 改造

```java
@Configuration
public class RestTemplateConfig {
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

`WorkbenchController` 中 URL 从 `http://localhost:8083` 改为 `http://knowledge-ai-service`，从 `http://localhost:8090` 改为 `http://collaboration-service`。

## 动态刷新

需要动态刷新的配置类加 `@RefreshScope` 或确保使用了 `@ConfigurationProperties`（天然支持刷新）：
- `AgentProperties`（LLM 参数）
- `MilvusClientConfiguration`（Milvus 地址）
- Workbench 中的服务 URL 配置

数据库连接池（Hikari）不自动刷新，改库地址需重启。

## 实施顺序

1. Docker 起 Nacos Server，控制台验证可用
2. 4 个服务 pom.xml 加依赖 + BOM
3. Nacos 控制台创建 `common-config.yaml`
4. 精简各服务 `application.yml`（加 nacos 连接，删共享配置）
5. Gateway 路由从 `localhost` 改为 `lb://`
6. Workbench `RestTemplate` 改为 `@LoadBalanced`，URL 改为服务名
7. 逐服务启动验证：注册状态、配置生效、Gateway 转发正常

## 风险与应对

| 风险 | 应对 |
|---|---|
| Nacos 不可用导致服务启动失败 | `fail-fast: false`，客户端有本地配置缓存 |
| Nacos 实例宕机 | Docker `--restart=always`，后续推集群模式 |
| WebFlux（Gateway）与 Nacos 兼容性 | Nacos Discovery Starter 原生兼容 WebFlux，已验证 |
| 敏感配置泄露 | JWT 密钥等继续走环境变量 / secrets 文件，不放 Nacos |

## 不做

- 不引入 `spring-cloud-starter-bootstrap`
- 不改造 Gateway 的 `IpAccessGlobalFilter` / 限流过滤器
- 不改变现有 JWT 认证 / RBAC 链路
- 不将密钥、密码明文放在 Nacos 中
