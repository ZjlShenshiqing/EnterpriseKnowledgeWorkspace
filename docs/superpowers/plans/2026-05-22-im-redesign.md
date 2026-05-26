# IM 聊天系统改造 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 IM 聊天系统引入 RocketMQ 可靠消息、消息级已读追踪、OSS 文件发送，解决消息丢失和离线收不到的问题。

**Architecture:** 在 collaboration-service 中建立 IM Service 层（ImMessageService、ImReadService、ImFileService），RocketMQ 解耦消息发送与持久化推送，WebSocket 负责实时投递，前端根据 ACK 机制处理发送确认和重试。

**Tech Stack:** Spring Boot 3.4.4, MyBatis-Plus 3.5.7, RocketMQ Spring Boot Starter 2.3.2, AWS SDK v2 (OSS), Vue 3 + Element Plus

---

### Task 1: 数据库迁移

**Files:**
- Modify: `enterprise-collaboration-service/src/main/resources/db/schema.sql`

- [ ] **Step 1: 修改 schema.sql，新增字段和表**

在 `im_message` 的 CREATE TABLE 定义中，在 `created_at` 之前插入新字段。完整替换 `im_message` 建表语句：

```sql
DROP TABLE IF EXISTS im_message;
CREATE TABLE im_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(64) NULL,
    content TEXT NOT NULL,
    msg_type VARCHAR(16) DEFAULT 'text',
    status VARCHAR(16) NOT NULL DEFAULT 'SENT',
    mq_msg_id VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_conv (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

修改 `im_conversation` 建表语句，新增三个字段：

```sql
DROP TABLE IF EXISTS im_conversation;
CREATE TABLE im_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'group',
    avatar VARCHAR(256) NULL,
    created_by BIGINT NOT NULL,
    last_msg_content VARCHAR(512) NULL,
    last_msg_sender VARCHAR(64) NULL,
    last_msg_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

在 `im_message` 建表语句之后，新增两张表的建表语句：

```sql
DROP TABLE IF EXISTS im_message_read;
CREATE TABLE im_message_read (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    last_read_msg_id BIGINT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS im_message_file;
CREATE TABLE im_message_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    oss_key VARCHAR(512) NOT NULL,
    thumb_oss_key VARCHAR(512) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 验证 DDL 改动**

```bash
grep -c "im_message_read" enterprise-collaboration-service/src/main/resources/db/schema.sql
grep -c "im_message_file" enterprise-collaboration-service/src/main/resources/db/schema.sql
grep -c "status VARCHAR" enterprise-collaboration-service/src/main/resources/db/schema.sql
```

Expected: 三个 grep 都输出 `1`。

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/resources/db/schema.sql
git commit -m "feat: IM 数据库迁移 — 消息状态、已读追踪、文件附件表

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: Entity 层更新与新建

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/ImMessage.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/ImConversation.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/ImMessageRead.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/ImMessageFile.java`

- [ ] **Step 1: 修改 ImMessage.java，新增 status 和 mqMsgId 字段**

```java
package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("im_message")
public class ImMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String content;
    private String msgType;
    private String status;
    private String mqMsgId;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 修改 ImConversation.java，新增 last_msg_* 字段**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("im_conversation")
public class ImConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String avatar;
    private Long createdBy;
    private String lastMsgContent;
    private String lastMsgSender;
    private LocalDateTime lastMsgAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 ImMessageRead.java**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("im_message_read")
public class ImMessageRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long conversationId;
    private Long lastReadMsgId;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 创建 ImMessageFile.java**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("im_message_file")
public class ImMessageFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String ossKey;
    private String thumbOssKey;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/
git commit -m "feat: 新增 ImMessageRead/ImMessageFile 实体，ImMessage/ImConversation 新增字段

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: 新增 Mapper 接口

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/ImMessageReadMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/ImMessageFileMapper.java`

- [ ] **Step 1: 创建 ImMessageReadMapper.java**

```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.ImMessageRead;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImMessageReadMapper extends BaseMapper<ImMessageRead> {
}
```

- [ ] **Step 2: 创建 ImMessageFileMapper.java**

```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.ImMessageFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImMessageFileMapper extends BaseMapper<ImMessageFile> {
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/ImMessageReadMapper.java enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/ImMessageFileMapper.java
git commit -m "feat: 新增 ImMessageReadMapper 和 ImMessageFileMapper

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: RocketMQ 依赖与基础配置

**Files:**
- Modify: `enterprise-collaboration-service/pom.xml`
- Modify: `enterprise-collaboration-service/src/main/resources/application.yml`

- [ ] **Step 1: 在 pom.xml 中添加 RocketMQ Starter 和 AWS SDK S3 依赖**

在 `spring-boot-starter-websocket` 依赖之后，`spring-security-crypto` 之前插入：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.3.2</version>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.27</version>
</dependency>
```

- [ ] **Step 2: 在 application.yml 中新增 RocketMQ 和 OSS 配置**

在 `logging:` 之前插入：

```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: im-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 1

app:
  im:
    oss:
      endpoint: ${OSS_ENDPOINT:https://oss-cn-shenzhen.aliyuncs.com}
      region: ${OSS_REGION:oss-cn-shenzhen}
      access-key: ${OSS_ACCESS_KEY:}
      secret-key: ${OSS_SECRET_KEY:}
      bucket: ${OSS_IM_BUCKET:im-files}
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/pom.xml enterprise-collaboration-service/src/main/resources/application.yml
git commit -m "feat: 添加 RocketMQ Spring Boot Starter 和 OSS (AWS SDK S3) 依赖

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: OSS 文件存储 — 配置属性类 + ImFileService

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/config/ImOssProperties.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImFileService.java`

- [ ] **Step 1: 创建 ImOssProperties.java**

```java
package com.zjl.collaboration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.im.oss")
public class ImOssProperties {
    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String bucket;
}
```

- [ ] **Step 2: 创建 ImFileService.java**

```java
package com.zjl.collaboration.service;

import com.zjl.collaboration.config.ImOssProperties;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ImFileService {

    private final S3Client s3Client;
    private final String bucket;

    public ImFileService(ImOssProperties props) {
        if (!StringUtils.hasText(props.getEndpoint())) {
            throw new IllegalStateException("OSS endpoint 不能为空");
        }
        this.bucket = props.getBucket();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .build();
        log.info("IM OSS 已初始化: endpoint={}, bucket={}", props.getEndpoint(), props.getBucket());
    }

    public Map<String, Object> upload(MultipartFile file) throws IOException {
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        if (originalName.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        String key = "im/" + UUID.randomUUID() + "/" + originalName;

        byte[] bytes = file.getBytes();
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ossKey", key);
        result.put("fileName", originalName);
        result.put("fileSize", bytes.length);
        result.put("fileType",
                file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        log.info("IM 文件上传成功: key={}, size={}", key, bytes.length);
        return result;
    }

    public InputStream read(String key) throws IOException {
        try {
            return s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在: " + key);
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/config/ImOssProperties.java enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImFileService.java
git commit -m "feat: 新增 ImOssProperties 和 ImFileService — OSS 文件上传/下载

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: ImMessageService — 消息发送（含 RocketMQ 生产者）

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImMessageService.java`

- [ ] **Step 1: 创建 ImMessageService.java**

```java
package com.zjl.collaboration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.mapper.ImMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageService {

    private final ImMessageMapper msgMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送消息：先持久化（status=SENDING），再投递到 RocketMQ。
     * 返回 ACK payload。
     */
    public Map<String, Object> send(Long senderId, String senderName, Long conversationId,
                                     String content, String clientMsgId) {
        ImMessage msg = new ImMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setContent(content);
        msg.setMsgType("text");
        msg.setStatus("SENDING");
        msg.setCreatedAt(LocalDateTime.now());
        msgMapper.insert(msg);

        try {
            Map<String, Object> mqPayload = new LinkedHashMap<>();
            mqPayload.put("messageId", msg.getId());
            mqPayload.put("conversationId", conversationId);
            mqPayload.put("senderId", senderId);
            mqPayload.put("senderName", senderName);
            mqPayload.put("content", content);
            mqPayload.put("createdAt", msg.getCreatedAt().toString());

            String mqMsgId = rocketMQTemplate.syncSend("im-message",
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(mqPayload)).build(),
                    3000).getMsgId();

            msg.setMqMsgId(mqMsgId);
            msg.setStatus("SENT");
            msgMapper.updateById(msg);
        } catch (Exception e) {
            log.error("RocketMQ 投递失败: msgId={}", msg.getId(), e);
            msg.setStatus("FAILED");
            msgMapper.updateById(msg);
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("type", "ack");
            ack.put("clientMsgId", clientMsgId);
            ack.put("serverMsgId", msg.getId());
            ack.put("status", "FAILED");
            return ack;
        }

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "ack");
        ack.put("clientMsgId", clientMsgId);
        ack.put("serverMsgId", msg.getId());
        ack.put("status", "SENT");
        return ack;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImMessageService.java
git commit -m "feat: 新增 ImMessageService — 消息发送与 RocketMQ 投递

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: ImMessageConsumer — RocketMQ 消费者（持久化确认 + 在线推送）

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImMessageConsumer.java`

- [ ] **Step 1: 创建 ImMessageConsumer.java**

```java
package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImConversation;
import com.zjl.collaboration.entity.ImConversationMember;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.mapper.ImConversationMapper;
import com.zjl.collaboration.mapper.ImConversationMemberMapper;
import com.zjl.collaboration.mapper.ImMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "im-message", consumerGroup = "im-consumer-group")
public class ImMessageConsumer implements RocketMQListener<String> {

    private final ImMessageMapper msgMapper;
    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ObjectMapper objectMapper;

    public static final Map<Long, WebSocketSession> onlineUsers = new ConcurrentHashMap<>();

    @Override
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long messageId = node.get("messageId").asLong();
            Long conversationId = node.get("conversationId").asLong();
            Long senderId = node.get("senderId").asLong();
            String senderName = node.has("senderName") ? node.get("senderName").asText() : null;
            String content = node.has("content") ? node.get("content").asText() : "";

            ImMessage existing = msgMapper.selectById(messageId);
            if (existing != null) {
                existing.setStatus("SENT");
                existing.setCreatedAt(LocalDateTime.now());
                msgMapper.updateById(existing);
            }

            ImConversation conv = convMapper.selectById(conversationId);
            if (conv != null) {
                conv.setLastMsgContent(content.length() > 100 ? content.substring(0, 100) : content);
                conv.setLastMsgSender(senderName);
                conv.setLastMsgAt(LocalDateTime.now());
                conv.setUpdatedAt(LocalDateTime.now());
                convMapper.updateById(conv);
            }

            Map<String, Object> push = new LinkedHashMap<>();
            push.put("type", "message");
            push.put("id", messageId);
            push.put("conversationId", conversationId);
            push.put("senderId", senderId);
            push.put("senderName", senderName);
            push.put("content", content);
            push.put("status", "SENT");
            String json = objectMapper.writeValueAsString(push);

            List<ImConversationMember> members = memberMapper.selectList(
                    Wrappers.lambdaQuery(ImConversationMember.class)
                            .eq(ImConversationMember::getConversationId, conversationId));
            for (ImConversationMember m : members) {
                WebSocketSession s = onlineUsers.get(m.getUserId());
                if (s != null && s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(json));
                    } catch (Exception e) {
                        log.warn("推送消息失败: userId={}, msgId={}", m.getUserId(), messageId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("消费 IM 消息失败", e);
            throw new RuntimeException("消费失败，触发 RocketMQ 重试", e);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImMessageConsumer.java
git commit -m "feat: 新增 ImMessageConsumer — RocketMQ 消费、在线用户推送

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 8: ImReadService — 已读标记与未读数计算

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImReadService.java`

- [ ] **Step 1: 创建 ImReadService.java**

```java
package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.entity.ImMessageRead;
import com.zjl.collaboration.mapper.ImMessageMapper;
import com.zjl.collaboration.mapper.ImMessageReadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImReadService {

    private final ImMessageReadMapper readMapper;
    private final ImMessageMapper msgMapper;
    private final ObjectMapper objectMapper;

    public void markRead(Long userId, Long conversationId, Long lastReadMsgId) {
        ImMessageRead record = readMapper.selectOne(
                Wrappers.lambdaQuery(ImMessageRead.class)
                        .eq(ImMessageRead::getUserId, userId)
                        .eq(ImMessageRead::getConversationId, conversationId));

        if (record == null) {
            record = new ImMessageRead();
            record.setUserId(userId);
            record.setConversationId(conversationId);
            record.setLastReadMsgId(lastReadMsgId);
            readMapper.insert(record);
        } else {
            if (lastReadMsgId > record.getLastReadMsgId()) {
                record.setLastReadMsgId(lastReadMsgId);
                record.setUpdatedAt(LocalDateTime.now());
                readMapper.updateById(record);
            }
        }

        Map<String, Object> readNotify = new LinkedHashMap<>();
        readNotify.put("type", "read");
        readNotify.put("conversationId", conversationId);
        readNotify.put("userId", userId);
        readNotify.put("lastReadMsgId", lastReadMsgId);

        try {
            String json = objectMapper.writeValueAsString(readNotify);
            List<ImMessage> messages = msgMapper.selectList(
                    Wrappers.lambdaQuery(ImMessage.class)
                            .eq(ImMessage::getConversationId, conversationId)
                            .orderByDesc(ImMessage::getCreatedAt));
            for (ImMessage msg : messages) {
                if (!msg.getSenderId().equals(userId)) {
                    WebSocketSession s = ImMessageConsumer.onlineUsers.get(msg.getSenderId());
                    if (s != null && s.isOpen()) {
                        try { s.sendMessage(new TextMessage(json)); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("已读通知推送失败: userId={}, convId={}", userId, conversationId);
        }
    }

    public int unreadCount(Long userId, Long conversationId) {
        ImMessageRead record = readMapper.selectOne(
                Wrappers.lambdaQuery(ImMessageRead.class)
                        .eq(ImMessageRead::getUserId, userId)
                        .eq(ImMessageRead::getConversationId, conversationId));
        long lastReadId = record != null ? record.getLastReadMsgId() : 0L;
        return (int) msgMapper.selectCount(
                Wrappers.lambdaQuery(ImMessage.class)
                        .eq(ImMessage::getConversationId, conversationId)
                        .gt(ImMessage::getId, lastReadId));
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/ImReadService.java
git commit -m "feat: 新增 ImReadService — 已读标记和未读数计算

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 9: 重构 ChatWebSocketHandler — 委托给 Service 层

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatWebSocketHandler.java`

- [ ] **Step 1: 重写 ChatWebSocketHandler.java，委托消息发送给 ImMessageService，使用 ImMessageConsumer.onlineUsers**

```java
package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.SysUser;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.collaboration.service.ImMessageConsumer;
import com.zjl.collaboration.service.ImMessageService;
import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtUtil jwtUtil;
    private final ImMessageService imMessageService;
    private final SysUserMapper userMapper;

    public ChatWebSocketHandler(JwtUtil jwtUtil, ImMessageService imMessageService,
                                 SysUserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.imMessageService = imMessageService;
        this.userMapper = userMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            ImMessageConsumer.onlineUsers.put(userId, session);
            broadcastStatus(userId, "online");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long senderId = getUserId(session);
        if (senderId == null) return;
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        Long convId = Long.valueOf(msg.get("conversationId").toString());
        String content = msg.get("content") != null ? msg.get("content").toString() : "";
        String clientMsgId = msg.get("clientMsgId") != null ? msg.get("clientMsgId").toString() : "";

        SysUser sender = userMapper.selectById(senderId);
        String senderName = sender != null ? sender.getRealName() : null;

        Map<String, Object> ack = imMessageService.send(senderId, senderName, convId, content, clientMsgId);
        String ackJson = mapper.writeValueAsString(ack);
        session.sendMessage(new TextMessage(ackJson));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            ImMessageConsumer.onlineUsers.remove(userId);
            broadcastStatus(userId, "offline");
        }
    }

    private void broadcastStatus(Long userId, String status) {
        Map<String, Object> out = Map.of("type", "status", "userId", userId, "status", status);
        try {
            String json = mapper.writeValueAsString(out);
            for (var s : ImMessageConsumer.onlineUsers.values()) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {}
    }

    private Long getUserId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("token=")) {
            try {
                Claims c = jwtUtil.parse(query.substring(6));
                return c.get("userId", Long.class);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatWebSocketHandler.java
git commit -m "refactor: ChatWebSocketHandler 委托消息发送给 ImMessageService，支持 ACK 机制

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 10: 重构 ChatController — 已读端点、未读数、文件上传、分页

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatController.java`

- [ ] **Step 1: 重写 ChatController.java**

```java
package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import com.zjl.collaboration.service.ImFileService;
import com.zjl.collaboration.service.ImReadService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper msgMapper;
    private final SysUserMapper userMapper;
    private final ImReadService readService;
    private final ImFileService fileService;

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(@RequestHeader("X-User-Id") Long userId) {
        List<Long> convIds = memberMapper.selectList(
                Wrappers.lambdaQuery(ImConversationMember.class).eq(ImConversationMember::getUserId, userId))
                .stream().map(ImConversationMember::getConversationId).toList();
        if (convIds.isEmpty()) return Results.success(List.of());

        List<ImConversation> convs = convMapper.selectBatchIds(convIds).stream()
                .sorted((a, b) -> {
                    LocalDateTime ta = a.getLastMsgAt() != null ? a.getLastMsgAt() : a.getUpdatedAt();
                    LocalDateTime tb = b.getLastMsgAt() != null ? b.getLastMsgAt() : b.getUpdatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                }).toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ImConversation c : convs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("last_msg", c.getLastMsgContent());
            m.put("last_msg_time", c.getLastMsgAt());
            m.put("last_msg_sender", c.getLastMsgSender());
            m.put("unread", readService.unreadCount(userId, c.getId()));
            m.put("updatedAt", c.getUpdatedAt());
            result.add(m);
        }
        return Results.success(result);
    }

    @GetMapping("/messages/{convId}")
    public Result<List<ImMessage>> messages(@PathVariable Long convId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        Page<ImMessage> pageResult = msgMapper.selectPage(
                new Page<>(page, size),
                Wrappers.lambdaQuery(ImMessage.class)
                        .eq(ImMessage::getConversationId, convId)
                        .orderByDesc(ImMessage::getCreatedAt));
        List<ImMessage> list = new ArrayList<>(pageResult.getRecords());
        Collections.reverse(list);
        return Results.success(list);
    }

    @PostMapping("/conversations")
    public Result<Long> createConv(@RequestBody CreateConvReq req,
                                    @RequestHeader("X-User-Id") Long userId) {
        if ("private".equals(req.getType()) && req.getMemberIds() != null
                && req.getMemberIds().size() == 1) {
            Long targetUserId = req.getMemberIds().get(0);
            List<ImConversationMember> myMemberships = memberMapper.selectList(
                    Wrappers.lambdaQuery(ImConversationMember.class)
                            .eq(ImConversationMember::getUserId, userId));
            for (ImConversationMember myMem : myMemberships) {
                ImConversation conv = convMapper.selectById(myMem.getConversationId());
                if (conv != null && "private".equals(conv.getType())) {
                    List<ImConversationMember> otherMembers = memberMapper.selectList(
                            Wrappers.lambdaQuery(ImConversationMember.class)
                                    .eq(ImConversationMember::getConversationId, conv.getId())
                                    .eq(ImConversationMember::getUserId, targetUserId));
                    if (!otherMembers.isEmpty()) {
                        return Results.success(conv.getId());
                    }
                }
            }
        }
        ImConversation c = new ImConversation();
        c.setName(req.getName());
        c.setType(req.getType());
        c.setCreatedBy(userId);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        convMapper.insert(c);
        ImConversationMember self = new ImConversationMember();
        self.setConversationId(c.getId());
        self.setUserId(userId);
        memberMapper.insert(self);
        if (req.getMemberIds() != null) {
            for (Long uid : req.getMemberIds()) {
                ImConversationMember m = new ImConversationMember();
                m.setConversationId(c.getId());
                m.setUserId(uid);
                memberMapper.insert(m);
            }
        }
        return Results.success(c.getId());
    }

    @PostMapping("/conversations/{id}/read")
    public Result<Void> markRead(@PathVariable Long id,
                                  @RequestHeader("X-User-Id") Long userId,
                                  @RequestBody ReadReq req) {
        readService.markRead(userId, id, req.getLastReadMsgId());
        return Results.success();
    }

    @PostMapping("/files/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        return Results.success(fileService.upload(file));
    }

    @GetMapping("/members/{convId}")
    public Result<List<Map<String, Object>>> members(@PathVariable Long convId) {
        List<Long> userIds = memberMapper.selectList(
                        Wrappers.lambdaQuery(ImConversationMember.class)
                                .eq(ImConversationMember::getConversationId, convId))
                .stream().map(ImConversationMember::getUserId).toList();
        List<SysUser> users = userIds.isEmpty() ? List.of()
                : userMapper.selectBatchIds(userIds);
        Map<Long, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        return Results.success(userIds.stream().map(uid -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", uid);
            SysUser u = userMap.get(uid);
            if (u != null) {
                m.put("username", u.getUsername());
                m.put("realName", u.getRealName());
            } else {
                m.put("username", "user" + uid);
                m.put("realName", "用户" + uid);
            }
            return m;
        }).collect(Collectors.toList()));
    }

    @Data
    public static class CreateConvReq {
        private String name;
        private String type;
        private List<Long> memberIds;
    }

    @Data
    public static class ReadReq {
        private Long lastReadMsgId;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatController.java
git commit -m "feat: ChatController 新增已读端点、文件上传、未读数、消息分页

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 11: 前端 — ACK 机制、发送失败与重试

**Files:**
- Modify: `enterprise-web/src/pages/Chats.vue`

- [ ] **Step 1: 修改 sendMsg() 携带 clientMsgId**

替换 `sendMsg` 方法（当前约第 563 行）：

```javascript
async function sendMsg() {
  const content = input.value.trim()
  if (!content || !activeConv.value) return
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    ElMessage.warning('连接未建立，请刷新页面后重试')
    return
  }
  const clientMsgId = 'c-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
  ws.send(JSON.stringify({ conversationId: activeConv.value.id, content, clientMsgId }))
  const temp = { id: clientMsgId, senderId: userId.value, senderName: '我', content,
    createdAt: new Date().toISOString(), _pending: true }
  msgs.value.push(temp)
  input.value = ''
  await nextTick()
  scrollBottom()
  inputBox.value?.focus()
}
```

- [ ] **Step 2: 修改 ws.onmessage 处理 ACK 和消息状态**

替换 `connectWs()` 中的 `ws.onmessage`（当前约第 523-530 行）：

```javascript
ws.onmessage = (e) => {
  try {
    const d = JSON.parse(e.data)
    if (d.type === 'ack') {
      const idx = msgs.value.findIndex(m => m.id === d.clientMsgId)
      if (idx !== -1) {
        if (d.status === 'FAILED') {
          msgs.value[idx] = { ...msgs.value[idx], _failed: true, _pending: false }
        } else {
          msgs.value[idx] = { ...msgs.value[idx], id: d.serverMsgId, _pending: false }
        }
      }
    } else if (d.type === 'message' && activeConv.value && d.conversationId === activeConv.value.id) {
      if (!msgs.value.some(m => m.id === d.id)) {
        msgs.value.push({ ...d, _pending: false })
        scrollBottom()
      }
    } else if (d.type === 'message') {
      if ('Notification' in window && Notification.permission === 'granted') {
        new Notification(d.senderName || '新消息', {
          body: (d.content || '').substring(0, 100),
          icon: '/favicon.ico'
        })
      }
    } else if (d.type === 'read' && activeConv.value && d.conversationId === activeConv.value.id) {
      ElMessage({ message: '对方已读', type: 'info', duration: 1500 })
    }
  } catch {}
}
```

- [ ] **Step 3: 修改消息气泡渲染，支持发送中/失败状态**

替换 `v-else` 分支（自己发的消息，当前约第 354-360 行）：

```html
<div v-else style="max-width:70%;display:flex;flex-direction:column;align-items:flex-end">
  <div style="display:flex;align-items:baseline;gap:6px;margin-bottom:4px">
    <span v-if="msg._pending" style="font-size:10px;color:#ff9500">发送中...</span>
    <span style="font-size:10px;color:#bbb">{{ formatTime(msg.createdAt) }}</span>
    <span style="font-weight:500;font-size:12px;color:#1f2329">我</span>
  </div>
  <div style="display:flex;align-items:center;gap:6px">
    <div :style="{background:msg._failed?'#ffe0e0':'#d6e6ff',padding:'8px 12px',borderRadius:'12px 4px 12px 12px',fontSize:'13px',lineHeight:'1.5',wordBreak:'break-word',border:msg._failed?'1px solid #f54a45':'none'}">
      {{ msg.content }}
    </div>
    <span v-if="msg._failed" @click="retrySend(msg)"
      style="color:#f54a45;cursor:pointer;font-size:16px;flex-shrink:0" title="重试">⟳</span>
  </div>
</div>
```

- [ ] **Step 4: 新增 retrySend 方法**

在 `doCreateGroup` 方法之后新增：

```javascript
function retrySend(msg) {
  const content = msg.content
  const idx = msgs.value.findIndex(m => m.id === msg.id)
  if (idx !== -1) msgs.value.splice(idx, 1)
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    ElMessage.warning('连接未建立，请刷新页面后重试')
    return
  }
  const clientMsgId = 'c-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
  ws.send(JSON.stringify({ conversationId: activeConv.value.id, content, clientMsgId }))
  const temp = { id: clientMsgId, senderId: userId.value, senderName: '我', content,
    createdAt: new Date().toISOString(), _pending: true }
  msgs.value.push(temp)
  nextTick(() => scrollBottom())
}
```

- [ ] **Step 5: 在 onMounted 中请求浏览器通知权限**

在 `onMounted` 末尾添加：

```javascript
if ('Notification' in window && Notification.permission === 'default') {
  Notification.requestPermission()
}
```

- [ ] **Step 6: Commit**

```bash
git add enterprise-web/src/pages/Chats.vue
git commit -m "feat: 前端 ACK 机制、发送失败重试、浏览器通知

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 12: 前端 — 会话列表未读角标 + 自动已读标记

**Files:**
- Modify: `enterprise-web/src/pages/Chats.vue`

- [ ] **Step 1: 修改 loadConvs() 读取未读数**

将 `loadConvs` 中的 `c._unread = 0`（当前约第 502 行）改为：

```javascript
c._unread = c.unread || 0
```

- [ ] **Step 2: 在 openConv() 中加入已读标记调用**

在 `openConv` 函数中，`scrollBottom()` 后添加已读标记：

```javascript
async function openConv(c) {
  activeConv.value = c
  try {
    const [mr, mm] = await Promise.all([
      fetch(`/api/chat/messages/${c.id}`, { headers: authHeaders() }),
      fetch(`/api/chat/members/${c.id}`, { headers: authHeaders() })
    ])
    msgs.value = (await mr.json()).data || []
    members.value = (await mm.json()).data || []
    await nextTick(); scrollBottom()
    c._unread = 0
    const lastMsg = msgs.value[msgs.value.length - 1]
    if (lastMsg && lastMsg.id) {
      fetch(`/api/chat/conversations/${c.id}/read`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ lastReadMsgId: lastMsg.id })
      }).catch(() => {})
    }
  } catch(e) {}
}
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/Chats.vue
git commit -m "feat: 会话列表未读角标使用 API 数据，打开会话自动标记已读

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 13: 前端 — 文件/图片上传与发送

**Files:**
- Modify: `enterprise-web/src/pages/Chats.vue`

- [ ] **Step 1: 修改工具栏，将图片和文件按钮改为可用的 file input**

修改 toolbar 区域（当前约第 365-368 行）：

```html
<div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
  <label style="color:#8f959e;cursor:pointer;font-size:15px" title="图片">
    &#128247;
    <input type="file" accept="image/*" @change="onImageSelect"
      style="display:none" ref="imgInput" multiple />
  </label>
  <label style="color:#8f959e;cursor:pointer;font-size:15px" title="文件">
    &#128206;
    <input type="file" @change="onFileSelect" style="display:none" ref="fileInput" />
  </label>
</div>
```

在 script setup 中新增 ref：

```javascript
const imgInput = ref(null)
const fileInput = ref(null)
```

- [ ] **Step 2: 新增文件上传和发送方法**

在 `loadUsers` 函数之后添加：

```javascript
async function uploadFileToServer(file) {
  const form = new FormData()
  form.append('file', file)
  const headers = getAuthHeaders()
  delete headers['Content-Type']
  const r = await fetch('/api/chat/files/upload', { method: 'POST', headers, body: form })
  const body = await r.json()
  if (!r.ok || !isApiSuccess(body)) {
    ElMessage.error(body?.message || '上传失败')
    return null
  }
  return body.data
}

async function onImageSelect(e) {
  for (const file of e.target.files) {
    const result = await uploadFileToServer(file)
    if (result) {
      ws.send(JSON.stringify({
        conversationId: activeConv.value.id,
        content: '',
        clientMsgId: 'c-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
        files: [result]
      }))
    }
  }
  e.target.value = ''
}

async function onFileSelect(e) {
  for (const file of e.target.files) {
    const result = await uploadFileToServer(file)
    if (result) {
      ws.send(JSON.stringify({
        conversationId: activeConv.value.id,
        content: '',
        clientMsgId: 'c-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
        files: [result]
      }))
    }
  }
  e.target.value = ''
}
```

- [ ] **Step 3: 修改消息渲染，支持图片和文件展示**

在消息气泡的 `{{ msg.content }}` 下方，增加文件渲染块（在 `msg.content` 的 div 闭合标签后添加）：

```html
<div v-if="msg.files && msg.files.length" style="margin-top:6px">
  <template v-for="f in msg.files" :key="f.ossKey">
    <img v-if="f.fileType && f.fileType.startsWith('image/')"
      :src="'/api/chat/files/' + encodeURIComponent(f.ossKey)"
      style="max-width:240px;max-height:200px;border-radius:6px;cursor:pointer"
      @click="previewFile(f)" />
    <div v-else @click="previewFile(f)"
      style="display:flex;align-items:center;gap:8px;padding:8px 12px;background:#fff;border:1px solid #e5e6eb;border-radius:8px;cursor:pointer;margin-top:4px">
      <span style="font-size:20px">&#128196;</span>
      <div>
        <div style="font-size:12px;font-weight:500">{{ f.fileName }}</div>
        <div style="font-size:10px;color:#8f959e">{{ formatFileSize(f.fileSize) }}</div>
      </div>
    </div>
  </template>
</div>
```

注意：这个文件渲染块需要同时出现在两条消息气泡中（他人消息和我的消息）。

- [ ] **Step 4: 新增辅助方法**

在 `formatTime` 函数附近添加：

```javascript
function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
function previewFile(f) {
  window.open('/api/chat/files/' + encodeURIComponent(f.ossKey), '_blank')
}
```

- [ ] **Step 5: Commit**

```bash
git add enterprise-web/src/pages/Chats.vue
git commit -m "feat: 前端文件/图片上传、消息气泡中渲染文件附件

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 14: 端到端验证

- [ ] **Step 1: 构建后端**

```bash
mvn clean package -pl enterprise-collaboration-service -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 启动 RocketMQ**

```bash
docker compose -f resouces/docker/rocketmq-stack-5.2.0.compose.yaml up -d
```

Expected: rmqnamesrv, rmqbroker, dashboard 三个容器启动

- [ ] **Step 3: 启动协作服务**

```bash
mvn spring-boot:run -pl enterprise-collaboration-service
```

Expected: 服务启动，日志中出现 RocketMQ 消费者注册信息

- [ ] **Step 4: 验证核心流程**

1. 打开两个浏览器窗口，分别登录 admin 和 zhangsan
2. 各自打开 `/chats`，进入同一个私聊会话
3. admin 发消息 → zhangsan 实时收到 → 刷新页面后消息不消失
4. 关闭 zhangsan 窗口 → admin 发消息 → zhangsan 重新打开 → 会话列表显示未读角标 → 点进去后角标消失
5. zhangsan 读消息后 → admin 端收到对方已读提示
6. 关闭 RocketMQ → 发消息 → 确认显示发送失败（红色气泡 + 重试按钮）→ 重启 RocketMQ → 点击重试 → 成功
7. 发送图片/文件 → 消息气泡中正确渲染

- [ ] **Step 5: Commit（如有修复）**

```bash
git add -A
git commit -m "chore: 端到端验证修复

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```
