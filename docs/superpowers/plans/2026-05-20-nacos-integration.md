# Nacos 微服务治理集成 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 Nacos 实现服务发现与配置管理，消除 Gateway 路由硬编码和分散配置。

**Architecture:** 4 个服务向 Nacos 注册。Gateway 通过 `lb://service-name` 路由，Spring Cloud LoadBalancer 做客户端负载均衡。共享配置（Redis、API Key、Milvus）放 Nacos `common-config.yaml`，数据库等专属配置放各服务自身的 Nacos Data ID。

**Tech Stack:** Spring Cloud Alibaba 2024.0.0.3, Nacos 2.5.1, Spring Cloud LoadBalancer (Gateway + Workbench)

---

### Task 1: Docker 启动 Nacos Server

**Files:** 无

- [ ] **Step 1: 拉取并启动 Nacos 容器**

```bash
docker run -d \
  --name nacos-standalone \
  --restart=always \
  -e MODE=standalone \
  -p 8848:8848 \
  -p 9848:9848 \
  nacos/nacos-server:v2.5.1
```

- [ ] **Step 2: 验证 Nacos 控制台可访问**

打开 `http://localhost:8848/nacos`，用 `nacos/nacos` 登录，确认能看到服务列表页（此时为空）。

- [ ] **Step 3: 验证 gRPC 端口**

```bash
curl -s http://localhost:9848/ | head -5
```

Expected: 返回 gRPC 相关信息（非 404 / connection refused）。

---

### Task 2: 四个服务添加 Nacos 依赖

**Files:**
- Modify: `enterprise-gateway-service/pom.xml`
- Modify: `enterprise-knowledge-ai-service/pom.xml`
- Modify: `enterprise-collaboration-service/pom.xml`
- Modify: `enterprise-workbench-service/pom.xml`

- [ ] **Step 1: Gateway pom.xml — 添加 BOM + 三个依赖**

在 `enterprise-gateway-service/pom.xml` 的 `<dependencyManagement>` 中，`spring-cloud-dependencies` BOM 之后新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2024.0.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

在 `<dependencies>` 末尾新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

- [ ] **Step 2: Knowledge-AI pom.xml — 添加 BOM + 两个依赖**

在 `enterprise-knowledge-ai-service/pom.xml` 的 `<dependencyManagement>` 中，`spring-ai-bom` BOM 之后新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2024.0.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

在 `<dependencies>` 末尾新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

- [ ] **Step 3: Collaboration pom.xml — 同样添加 BOM + 两个依赖**

在 `enterprise-collaboration-service/pom.xml` 的 `<dependencyManagement>` 中，`spring-boot-dependencies` BOM 之后新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2024.0.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

在 `<dependencies>` 末尾新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

- [ ] **Step 4: Workbench pom.xml — 同样添加 BOM + 三个依赖**

在 `enterprise-workbench-service/pom.xml` 的 `<dependencyManagement>` 中，`spring-boot-dependencies` BOM 之后新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2024.0.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

在 `<dependencies>` 末尾新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

- [ ] **Step 5: 验证依赖解析**

```bash
export JAVA_HOME=$(/usr/libexec/java_home) && \
/Users/zjl/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn \
dependency:resolve -pl enterprise-gateway-service,enterprise-knowledge-ai-service,enterprise-collaboration-service,enterprise-workbench-service -am 2>&1 | tail -10
```

Expected: BUILD SUCCESS，无 `Could not resolve` 错误。

- [ ] **Step 6: Commit**

```bash
git add enterprise-gateway-service/pom.xml \
        enterprise-knowledge-ai-service/pom.xml \
        enterprise-collaboration-service/pom.xml \
        enterprise-workbench-service/pom.xml
git commit -m "feat: 添加 spring-cloud-alibaba nacos-discovery + nacos-config 依赖

四个服务统一引入 Spring Cloud Alibaba 2024.0.0.3 BOM 及 nacos-discovery、
nacos-config starter。Gateway 和 Workbench 额外引入 loadbalancer。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Nacos 控制台创建配置

**Files:** 无（Nacos 控制台操作 + 说明文档）

- [ ] **Step 1: 创建共享配置 `common-config.yaml`**

在 Nacos 控制台 (`http://localhost:8848/nacos`) → 配置管理 → 配置列表 → 点击 `+`：

- Data ID: `common-config.yaml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`
- 配置内容:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
app:
  milvus:
    uri: http://localhost:19530
    collection: kb_chunk_embedding
    vector-dimension: 4096
    fail-on-init: false
  agent:
    llm:
      provider: deepseek
      api-key: ${DEEPSEEK_API_KEY:}
      base-url: https://api.deepseek.com
      model: deepseek-chat
      max-tokens: 4096
      temperature: 0.3
    session:
      max-history: 50
      archive-after-days: 30
    web-search:
      enabled: true
      api-key: ${WEB_SEARCH_API_KEY:sk-0d6ebe5d5f9d4746ad3d3cd4a36a7e53}
      base-url: https://api.bochaai.com/v1
      count: 8
      freshness: noLimit
collab:
  service:
    url: http://enterprise-collaboration-service
knowledge:
  service:
    url: http://enterprise-knowledge-ai-service
```

- [ ] **Step 2: 创建 Gateway 专属配置**

点击 `+`：

- Data ID: `enterprise-gateway-service.yaml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`
- 配置内容:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/enterprise_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 3: 创建 Knowledge-AI 专属配置**

点击 `+`：

- Data ID: `enterprise-knowledge-ai-service.yaml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`
- 配置内容:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/enterprise_knowledge_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 4: 创建 Collaboration 专属配置**

点击 `+`：

- Data ID: `enterprise-collaboration-service.yaml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`
- 配置内容:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/enterprise_collaboration?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 5: 创建 Workbench 专属配置**

点击 `+`：

- Data ID: `enterprise-workbench-service.yaml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`
- 配置内容:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/enterprise_collaboration?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 6: Commit（文档说明）**

本次无代码文件变更，在后续应用 yml 修改的 commit 中一并提交。

---

### Task 4: Gateway 配置改造

**Files:**
- Modify: `enterprise-gateway-service/src/main/resources/application.yml`

- [ ] **Step 1: 更新 application.yml**

替换整个文件内容为：

```yaml
spring:
  application:
    name: enterprise-gateway-service
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
    gateway:
      default-filters:
        - PreserveHostHeader
      routes:
        - id: knowledge-ai
          uri: lb://enterprise-knowledge-ai-service
          predicates:
            - Path=/api/kb/**,/api/ai-qa/**
        - id: collaboration
          uri: lb://enterprise-collaboration-service
          predicates:
            - Path=/api/meetings/**,/api/todos/**,/api/tasks/**,/api/notifications/**
        - id: workbench
          uri: lb://enterprise-workbench-service
          predicates:
            - Path=/api/workbench/**

  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

server:
  port: 8086

management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
  endpoint:
    health:
      show-details: always

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger - %msg%n"
```

变更说明：
- 删除本地 `datasource` 配置（已移动到 Nacos `enterprise-gateway-service.yaml`）
- 新增 `spring.cloud.nacos.*`（discovery + config 连接）
- 路由 `uri` 从 `http://localhost:8081` / `http://localhost:8083` 改为 `lb://enterprise-knowledge-ai-service` / `lb://enterprise-workbench-service`（修复端口错误的同时启用服务发现）
- collaboration 路由也从 `http://localhost:8090` 改为 `lb://enterprise-collaboration-service`

- [ ] **Step 2: Commit**

```bash
git add enterprise-gateway-service/src/main/resources/application.yml
git commit -m "feat(gateway): 路由从硬编码 URL 改为 Nacos lb:// 服务发现

- 路由 uri 改为 lb://service-name，由 Spring Cloud LoadBalancer 从 Nacos 解析实例
- 修复 knowledge-ai 路由端口 8081→正确的服务名解析
- 修复 workbench 路由端口 8083→正确的服务名解析
- datasource 配置移到 Nacos enterprise-gateway-service.yaml
- 新增 nacos discovery + config 连接配置

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: Knowledge-AI 配置改造

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/resources/application.yml`

- [ ] **Step 1: 更新 application.yml**

替换整个文件内容为：

```yaml
spring:
  config:
    import: optional:classpath:application-secrets.yml
  application:
    name: enterprise-knowledge-ai-service
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
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

server:
  port: 8083

mybatis-plus:
  mapper-locations: classpath:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

app:
  kb:
    upload-dir: ./data/kb-uploads

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

变更说明：
- 删除本地 `datasource` 配置（移到 Nacos `enterprise-knowledge-ai-service.yaml`）
- 删除本地 `app.knowledge.*`、`app.milvus.*`、`app.agent.*`（移到 Nacos `common-config.yaml`）
- 新增 `spring.cloud.nacos.*`
- 保留本地: `server.port`、`mybatis-plus`、`spring.servlet.multipart`、`spring.sql.init`、`app.kb.upload-dir`

- [ ] **Step 2: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/resources/application.yml
git commit -m "feat(knowledge-ai): 配置迁移到 Nacos，本地只保留专属配置

datasource → Nacos enterprise-knowledge-ai-service.yaml
milvus/agent/llm/web-search → Nacos common-config.yaml
本地保留: multipart, sql.init, mybatis-plus, upload-dir

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: Collaboration 配置改造

**Files:**
- Modify: `enterprise-collaboration-service/src/main/resources/application.yml`

- [ ] **Step 1: 更新 application.yml**

替换整个文件内容为：

```yaml
spring:
  config:
    import: optional:classpath:application-secrets.yml
  application:
    name: enterprise-collaboration-service
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
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql

server:
  port: 8090

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

auth:
  jwt:
    expiration: 86400000
```

变更说明：
- 删除本地 `datasource` 配置（移到 Nacos `enterprise-collaboration-service.yaml`）
- 删除本地 `spring.data.redis` 配置（移到 Nacos `common-config.yaml`）
- 新增 `spring.cloud.nacos.*`
- 保留本地: `server.port`、`mybatis-plus`、`spring.sql.init`、`auth.jwt.expiration`

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/resources/application.yml
git commit -m "feat(collaboration): 配置迁移到 Nacos

datasource → Nacos enterprise-collaboration-service.yaml
redis → Nacos common-config.yaml
本地保留: sql.init, mybatis-plus, jwt expiration

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: Workbench 配置 + 代码改造

**Files:**
- Modify: `enterprise-workbench-service/src/main/resources/application.yml`
- Create: `enterprise-workbench-service/src/main/java/com/zjl/workbench/config/RestTemplateConfig.java`
- Modify: `enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java`

- [ ] **Step 1: 更新 application.yml**

替换整个文件内容为：

```yaml
spring:
  config:
    import: optional:classpath:application-secrets.yml
  application:
    name: enterprise-workbench-service
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
  port: 8084

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

变更说明：
- 删除本地 `datasource`（移到 Nacos `enterprise-workbench-service.yaml`）
- 删除本地 `spring.data.redis`（移到 Nacos `common-config.yaml`）
- 删除本地 `knowledge.service.url`（移到 Nacos `common-config.yaml` 的 `knowledge.service.url`）
- 新增 `spring.cloud.nacos.*`

- [ ] **Step 2: 创建 RestTemplateConfig**

创建文件 `enterprise-workbench-service/src/main/java/com/zjl/workbench/config/RestTemplateConfig.java`：

```java
package com.zjl.workbench.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 3: 改造 WorkbenchController — 注入 RestTemplate + 改用服务名**

修改 `WorkbenchController.java`：

删除第 17 行：
```java
private final RestTemplate rt = new RestTemplate();
```

改为注入 Bean 并调整 URL 变量默认值：

```java
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final RestTemplate rt;

    @Value("${collab.service.url:http://enterprise-collaboration-service}")
    private String collabUrl;

    @Value("${knowledge.service.url:http://enterprise-knowledge-ai-service}")
    private String knowledgeUrl;

    public WorkbenchController(RestTemplate restTemplate) {
        this.rt = restTemplate;
    }
    // ... 其余代码不变
```

注意：只需要改类顶部的字段声明和构造函数。`callList()` 和 `overview()` / `stats()` 方法中的 `rt.getForObject()` 调用无需改动 — `@LoadBalanced` 透明拦截 `http://enterprise-collaboration-service/api/...` 这类 URL。

- [ ] **Step 4: Commit**

```bash
git add enterprise-workbench-service/src/main/resources/application.yml \
        enterprise-workbench-service/src/main/java/com/zjl/workbench/config/RestTemplateConfig.java \
        enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java
git commit -m "feat(workbench): 服务发现改造 — RestTemplate 加 @LoadBalanced

- 新增 RestTemplateConfig，创建 @LoadBalanced RestTemplate Bean
- WorkbenchController 注入 RestTemplate Bean 替代 new RestTemplate()
- collabUrl/knowledgeUrl 默认值改为服务名，由 LoadBalancer 解析
- redis/datasource 配置迁移到 Nacos

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 8: 编译验证

**Files:** 无

- [ ] **Step 1: 全模块编译**

```bash
export JAVA_HOME=$(/usr/libexec/java_home) && \
/Users/zjl/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn \
clean compile -DskipTests 2>&1 | tail -10
```

Expected: BUILD SUCCESS（全 8 模块通过）。

- [ ] **Step 2: 验证 Nacos 依赖无冲突**

```bash
export JAVA_HOME=$(/usr/libexec/java_home) && \
/Users/zjl/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn \
dependency:tree -pl enterprise-gateway-service -Dincludes=com.alibaba.cloud,com.alibaba.nacos 2>&1 | grep -E "nacos|alibaba" | head -10
```

Expected: 能看到 `spring-cloud-starter-alibaba-nacos-discovery`、`nacos-client` 等依赖，版本为 2.5.x。

---

### Task 9: 启动验证

**Files:** 无

- [ ] **Step 1: 启动 Knowledge-AI 服务**

```bash
export JAVA_HOME=$(/usr/libexec/java_home) && \
/Users/zjl/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn \
spring-boot:run -pl enterprise-knowledge-ai-service
```

验证：
- 日志中出现 `nacos registry` 或注册相关日志
- Nacos 控制台 → 服务管理 → 服务列表 中出现 `enterprise-knowledge-ai-service`，实例数 1
- 服务 health endpoint 正常：`curl http://localhost:8083/actuator/health`

- [ ] **Step 2: 依次启动其余三个服务**

```bash
# 终端 2: Collaboration
mvn spring-boot:run -pl enterprise-collaboration-service

# 终端 3: Workbench
mvn spring-boot:run -pl enterprise-workbench-service

# 终端 4: Gateway
mvn spring-boot:run -pl enterprise-gateway-service
```

验证 Nacos 控制台出现 4 个服务，每个 1 个实例，状态健康。

- [ ] **Step 3: 通过 Gateway 调用验证路由**

```bash
# 测试知识库接口（需携带有效 JWT）
curl -s http://localhost:8086/actuator/health | head -5

# 测试知识库路由转发
curl -s http://localhost:8086/api/kb/documents?current=1\&size=1 \
  -H "Authorization: Bearer <valid-token>"
```

Expected: 返回正常 JSON 响应（不是 404 / 503），说明 `lb://enterprise-knowledge-ai-service` 路由生效。

- [ ] **Step 4: 验证 Workbench 聚合调用**

如果 Nacos 未启动但 Workbench 启动，`http://enterprise-knowledge-ai-service` 会解析失败，此时 `/api/workbench/stats` 应返回部分数据（`docCount: 0`、`inProgressTaskCount: 0` 等），不会整个接口崩溃 — 因为 Workbench 有 try-catch 兜底。

- [ ] **Step 5: 验证配置动态刷新**

在 Nacos 控制台修改 `common-config.yaml` 中的 `app.agent.llm.temperature: 0.3` 改为 `0.8`，发布。

查看 Knowledge-AI 日志中是否有配置刷新相关日志（`Refresh keys changed` 等）。`AgentProperties` 使用了 `@ConfigurationProperties`，Nacos Config 会自动推送变更。

---

### Task 10: 回退与调试指南

**说明文档（不产生代码变更）**

- [ ] **回退到硬编码路由**

将 Gateway `application.yml` 中路由 `uri` 从 `lb://xxx` 改回 `http://localhost:PORT`，删除 `spring.cloud.nacos.*`，恢复各服务的 `application.yml` 为改动前版本。

- [ ] **调试技巧**

```bash
# 查看 Nacos 注册的服务列表
curl -s http://localhost:8848/nacos/v1/ns/service/list?pageNo=1\&pageSize=20

# 查看某个服务的实例详情
curl -s http://localhost:8848/nacos/v1/ns/instance/list?serviceName=enterprise-knowledge-ai-service

# Gateway 查看路由表
curl -s http://localhost:8086/actuator/gateway/routes | python3 -m json.tool
```
