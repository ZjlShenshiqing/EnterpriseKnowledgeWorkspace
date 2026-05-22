# 文档协作 — 实时协同编辑 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文档管理从单人 CRUD 升级为多人实时协同编辑，支持 OT 冲突解决、光标同步、评论和分享权限。

**Architecture:** 后端在 collaboration-service 中新增 OT 引擎（Quill Delta 格式）+ WebSocket 端点 `/ws/docs` + 评论/分享/协作者 REST API。前端将 contenteditable 替换为 Quill 编辑器，通过自定义 WebSocket 协议做实时同步，右侧面板接入真实 API。OT 变换在服务端完成，并发通过 per-docId 锁控制，每 50 版本快照一次。

**Tech Stack:** Java 17 / Spring Boot 3.4.4 / MyBatis-Plus / WebSocket (TextWebSocketHandler) / Vue 3 / Quill / quill-cursors / JSON (Jackson)

---

## 文件结构

```
enterprise-collaboration-service/src/main/java/com/zjl/collaboration/
  entity/
    SysDoc.java                  [修改] 新增 version, snapshotVersion 字段
    SysDocOperation.java         [新建] OT 操作日志实体
    SysDocComment.java           [新建] 评论实体
    SysDocShareLink.java         [新建] 分享链接实体
    SysDocCollaborator.java      [新建] 协作者实体
  mapper/
    SysDocMapper.java            [无变化]
    SysDocOperationMapper.java   [新建]
    SysDocCommentMapper.java     [新建]
    SysDocShareLinkMapper.java   [新建]
    SysDocCollaboratorMapper.java [新建]
  config/
    WebSocketConfig.java         [修改] 注册 /ws/docs
  web/
    DocController.java           [修改] 改造 CRUD，返回 version
    DocCommentController.java    [新建] 评论 CRUD
    DocShareController.java      [新建] 分享链接 + 协作者管理
    DocWebSocketHandler.java     [新建] OT WebSocket 处理
  service/
    DocOTService.java            [新建] OT 变换引擎
    DocPresenceService.java      [新建] 在线状态管理
    DocPermissionService.java    [新建] 权限检查

enterprise-web/src/
  pages/
    Documents.vue                [修改] Quill 编辑器 + WebSocket + 真实面板

enterprise-collaboration-service/src/main/resources/
  db/migration/
    003-doc-collaboration.sql    [新建] 数据库变更
```

---

### Task 1: 数据库迁移 — 新增表和字段

**Files:**
- Create: `enterprise-collaboration-service/src/main/resources/db/migration/003-doc-collaboration.sql`

- [ ] **Step 1: 编写迁移 SQL**

```sql
-- 改造 sys_doc 表
ALTER TABLE sys_doc
  ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '当前操作版本号',
  ADD COLUMN snapshot_version INT NOT NULL DEFAULT 0 COMMENT '最后快照版本号';

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_doc_operation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  version INT NOT NULL,
  operation LONGTEXT NOT NULL COMMENT 'Quill Delta JSON',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_doc_version (doc_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评论表
CREATE TABLE IF NOT EXISTS sys_doc_comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  anchor_index INT DEFAULT NULL COMMENT '锚定起始位置',
  anchor_length INT DEFAULT NULL COMMENT '锚定长度',
  parent_id BIGINT DEFAULT NULL COMMENT '回复目标评论ID',
  resolved TINYINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_doc_comment (doc_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分享链接表
CREATE TABLE IF NOT EXISTS sys_doc_share_link (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_id BIGINT NOT NULL,
  token VARCHAR(64) NOT NULL,
  permission VARCHAR(10) NOT NULL COMMENT 'VIEW/COMMENT/EDIT',
  expired_at DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 协作者表
CREATE TABLE IF NOT EXISTS sys_doc_collaborator (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_id BIGINT NOT NULL,
  target_type VARCHAR(10) NOT NULL COMMENT 'USER/DEPT',
  target_id BIGINT NOT NULL,
  permission VARCHAR(10) NOT NULL COMMENT 'VIEW/COMMENT/EDIT',
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_doc_collaborator (doc_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/resources/db/migration/003-doc-collaboration.sql
git commit -m "feat: 文档协作数据库迁移 — 新增操作日志/评论/分享/协作者表"
```

---

### Task 2: 实体类 — 新增和改造

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDoc.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDocOperation.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDocComment.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDocShareLink.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDocCollaborator.java`

- [ ] **Step 1: 改造 SysDoc — 新增 version 和 snapshotVersion 字段**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc")
public class SysDoc {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private Long updatedBy;
    private String updatedByName;
    private Integer version;
    private Integer snapshotVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 SysDocOperation**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_operation")
public class SysDocOperation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long userId;
    private Integer version;
    private String operation;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 SysDocComment**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_comment")
public class SysDocComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long userId;
    private String content;
    private Integer anchorIndex;
    private Integer anchorLength;
    private Long parentId;
    private Integer resolved;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 创建 SysDocShareLink**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_share_link")
public class SysDocShareLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String token;
    private String permission;
    private LocalDateTime expiredAt;
    private Integer deleted;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 SysDocCollaborator**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_collaborator")
public class SysDocCollaborator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String targetType;
    private Long targetId;
    private String permission;
    private Integer deleted;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/
git commit -m "feat: 文档协作实体类 — 操作日志/评论/分享/协作者 + sys_doc 新增 version"
```

---

### Task 3: Mapper 接口

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysDocOperationMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysDocCommentMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysDocShareLinkMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysDocCollaboratorMapper.java`

- [ ] **Step 1: 创建四个 Mapper 接口**

`SysDocOperationMapper.java`:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.SysDocOperation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysDocOperationMapper extends BaseMapper<SysDocOperation> {}
```

`SysDocCommentMapper.java`:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.SysDocComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysDocCommentMapper extends BaseMapper<SysDocComment> {}
```

`SysDocShareLinkMapper.java`:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.SysDocShareLink;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysDocShareLinkMapper extends BaseMapper<SysDocShareLink> {}
```

`SysDocCollaboratorMapper.java`:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.SysDocCollaborator;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysDocCollaboratorMapper extends BaseMapper<SysDocCollaborator> {}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/
git commit -m "feat: 文档协作 Mapper 接口 — 操作日志/评论/分享/协作者"
```

---

### Task 4: OT 变换引擎 — DocOTService

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocOTService.java`

DocOTService 是核心，负责接收客户端提交的 Quill Delta 操作，与服务端最新状态做 OT 变换，返回变换后的操作并持久化。Quill Delta 格式为 `{"ops": [{"retain": N}, {"insert": "text"}, {"delete": N}, ...]}`。

- [ ] **Step 1: 创建 DocOTService**

```java
package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.entity.SysDocOperation;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysDocOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocOTService {

    private static final int SNAPSHOT_INTERVAL = 50;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SysDocMapper docMapper;
    private final SysDocOperationMapper docOperationMapper;
    private final ConcurrentHashMap<Long, ReentrantLock> docLocks = new ConcurrentHashMap<>();

    /** 提交操作：做 OT 变换后持久化并返回变换后的 op */
    public JsonNode submitOperation(Long docId, Long userId, JsonNode ops, int baseVersion) {
        ReentrantLock lock = docLocks.computeIfAbsent(docId, k -> new ReentrantLock());
        lock.lock();
        try {
            SysDoc doc = docMapper.selectById(docId);
            if (doc == null) {
                throw new IllegalArgumentException("文档不存在: " + docId);
            }

            int currentVersion = doc.getVersion() != null ? doc.getVersion() : 0;

            if (baseVersion > currentVersion) {
                throw new IllegalStateException(
                    "版本冲突: baseVersion=" + baseVersion + " > currentVersion=" + currentVersion);
            }

            List<Map<String, Object>> opList = opsToMapList(ops);

            if (baseVersion < currentVersion) {
                List<Map<String, Object>> concurrentOps = loadOpsSinceVersion(docId, baseVersion);
                for (Map<String, Object> concurrentOp : concurrentOps) {
                    opList = transform(opList, concurrentOp, true);
                }
            }

            JsonNode transformedOps = mapListToOps(opList);

            int newVersion = currentVersion + 1;
            SysDocOperation record = new SysDocOperation();
            record.setDocId(docId);
            record.setUserId(userId);
            record.setVersion(newVersion);
            record.setOperation(transformedOps.toString());
            docOperationMapper.insert(record);

            doc.setVersion(newVersion);
            if (newVersion - (doc.getSnapshotVersion() != null ? doc.getSnapshotVersion() : 0) >= SNAPSHOT_INTERVAL) {
                String newContent = applyOpsToContent(doc.getContent(), opList);
                doc.setContent(newContent);
                doc.setSnapshotVersion(newVersion);
            }
            docMapper.updateById(doc);

            return transformedOps;
        } finally {
            lock.unlock();
        }
    }

    /** 获取文档加载信息：快照内容 + version */
    public DocSnapshot getDocument(Long docId) {
        SysDoc doc = docMapper.selectById(docId);
        if (doc == null) return null;
        return new DocSnapshot(doc.getContent(), doc.getVersion() != null ? doc.getVersion() : 0);
    }

    /** 获取指定版本之后的增量 ops */
    public List<Map<String, Object>> getOpsSinceVersion(Long docId, int sinceVersion) {
        return loadOpsSinceVersion(docId, sinceVersion);
    }

    /** Quill Delta OT 变换算法 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> transform(
            List<Map<String, Object>> opA,
            List<Map<String, Object>> opB,
            boolean isLeft) {
        List<Map<String, Object>> result = new ArrayList<>();

        int indexA = 0;
        int offsetA = 0;
        int indexB = 0;
        int offsetB = 0;

        while (indexA < opA.size() && indexB < opB.size()) {
            Map<String, Object> curOpA = new java.util.LinkedHashMap<>(opA.get(indexA));
            Map<String, Object> curOpB = new java.util.LinkedHashMap<>(opB.get(indexB));

            if (curOpA.containsKey("insert")) {
                int len = getOpLength(curOpA);
                result.add(curOpA);
                offsetA += len;
                if (offsetA >= getOpLength(opA.get(indexA))) {
                    indexA++;
                    offsetA = 0;
                }
                continue;
            }
            if (curOpB.containsKey("insert")) {
                int lenB = getOpLength(curOpB);
                Map<String, Object> retainOp = new java.util.LinkedHashMap<>();
                retainOp.put("retain", lenB);
                result.add(retainOp);
                offsetB += lenB;
                if (offsetB >= getOpLength(opB.get(indexB))) {
                    indexB++;
                    offsetB = 0;
                }
                continue;
            }

            int lenA = getOpLength(curOpA) - offsetA;
            int lenB = getOpLength(curOpB) - offsetB;
            int minLen = Math.min(lenA, lenB);

            if (curOpA.containsKey("retain") && curOpB.containsKey("retain")) {
                Map<String, Object> retainOp = new java.util.LinkedHashMap<>();
                retainOp.put("retain", minLen);
                result.add(retainOp);
                offsetA += minLen;
                offsetB += minLen;
            } else if (curOpA.containsKey("delete") && curOpB.containsKey("delete")) {
                offsetA += minLen;
                offsetB += minLen;
            } else if (curOpA.containsKey("delete") && curOpB.containsKey("retain")) {
                Map<String, Object> deleteOp = new java.util.LinkedHashMap<>();
                deleteOp.put("delete", minLen);
                result.add(deleteOp);
                offsetA += minLen;
                offsetB += minLen;
            } else if (curOpA.containsKey("retain") && curOpB.containsKey("delete")) {
                offsetA += minLen;
                offsetB += minLen;
            }

            if (offsetA >= getOpLength(opA.get(indexA))) {
                indexA++;
                offsetA = 0;
            }
            if (offsetB >= getOpLength(opB.get(indexB))) {
                indexB++;
                offsetB = 0;
            }
        }

        while (indexA < opA.size()) {
            Map<String, Object> curOpA = opA.get(indexA);
            if (curOpA.containsKey("insert")) {
                result.add(new java.util.LinkedHashMap<>(curOpA));
            } else if (curOpA.containsKey("delete")) {
                result.add(new java.util.LinkedHashMap<>(curOpA));
            } else if (curOpA.containsKey("retain")) {
                int retainVal = ((Number) curOpA.get("retain")).intValue();
                int remaining = retainVal - offsetA;
                if (remaining > 0) {
                    Map<String, Object> retainOp = new java.util.LinkedHashMap<>();
                    retainOp.put("retain", remaining);
                    result.add(retainOp);
                }
            }
            indexA++;
            offsetA = 0;
        }

        return result;
    }

    private int getOpLength(Map<String, Object> op) {
        if (op.containsKey("retain")) {
            Object val = op.get("retain");
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }
        if (op.containsKey("delete")) {
            Object val = op.get("delete");
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }
        if (op.containsKey("insert")) {
            Object val = op.get("insert");
            if (val instanceof String) return ((String) val).length();
            if (val instanceof Map) return 1;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> opsToMapList(JsonNode ops) {
        try {
            return OBJECT_MAPPER.convertValue(ops, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析 ops 失败", e);
        }
    }

    private JsonNode mapListToOps(List<Map<String, Object>> ops) {
        return OBJECT_MAPPER.valueToTree(ops);
    }

    private List<Map<String, Object>> loadOpsSinceVersion(Long docId, int sinceVersion) {
        List<SysDocOperation> records = docOperationMapper.selectList(
            new LambdaQueryWrapper<SysDocOperation>()
                .eq(SysDocOperation::getDocId, docId)
                .gt(SysDocOperation::getVersion, sinceVersion)
                .orderByAsc(SysDocOperation::getVersion));
        List<Map<String, Object>> ops = new ArrayList<>();
        for (SysDocOperation record : records) {
            try {
                List<Map<String, Object>> op = OBJECT_MAPPER.readValue(
                    record.getOperation(),
                    new TypeReference<List<Map<String, Object>>>() {});
                ops.addAll(op);
            } catch (JsonProcessingException e) {
                log.error("解析操作日志失败: {}", record.getId(), e);
            }
        }
        return ops;
    }

    @SuppressWarnings("unchecked")
    private String applyOpsToContent(String content, List<Map<String, Object>> ops) {
        try {
            JsonNode deltaNode = OBJECT_MAPPER.readTree(content);
            List<Map<String, Object>> delta = OBJECT_MAPPER.convertValue(
                deltaNode.path("ops"),
                new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> composed = composeDeltas(delta, ops);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("ops", composed);
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.error("应用操作到内容失败", e);
            return content;
        }
    }

    private List<Map<String, Object>> composeDeltas(
            List<Map<String, Object>> base,
            List<Map<String, Object>> delta) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> op : base) {
            result.add(new java.util.LinkedHashMap<>(op));
        }
        for (Map<String, Object> op : delta) {
            result.add(new java.util.LinkedHashMap<>(op));
        }
        return simplifyOps(result);
    }

    private List<Map<String, Object>> simplifyOps(List<Map<String, Object>> ops) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> op : ops) {
            if (result.isEmpty()) {
                result.add(op);
                continue;
            }
            Map<String, Object> last = result.get(result.size() - 1);
            if (last.containsKey("retain") && op.containsKey("retain")) {
                int sum = ((Number) last.get("retain")).intValue() + ((Number) op.get("retain")).intValue();
                last.put("retain", sum);
            } else if (last.containsKey("delete") && op.containsKey("delete")) {
                int sum = ((Number) last.get("delete")).intValue() + ((Number) op.get("delete")).intValue();
                last.put("delete", sum);
            } else if (last.containsKey("insert") && op.containsKey("insert")) {
                Object lastInsert = last.get("insert");
                Object thisInsert = op.get("insert");
                if (lastInsert instanceof String && thisInsert instanceof String) {
                    last.put("insert", (String) lastInsert + (String) thisInsert);
                } else {
                    result.add(op);
                }
            } else {
                result.add(op);
            }
        }
        return result;
    }

    public record DocSnapshot(String content, int version) {}
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocOTService.java
git commit -m "feat: OT 变换引擎 — Quill Delta OT 变换/快照/操作日志"
```

---

### Task 5: 在线状态服务 — DocPresenceService

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocPresenceService.java`

- [ ] **Step 1: 创建 DocPresenceService**

```java
package com.zjl.collaboration.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocPresenceService {

    /** docId → { sessionId → session } */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SessionInfo>> docSessions = new ConcurrentHashMap<>();

    /** 添加会话到文档房间 */
    public void join(Long docId, String sessionId, Long userId, String userName, WebSocketSession session) {
        docSessions.computeIfAbsent(docId, k -> new ConcurrentHashMap<>())
            .put(sessionId, new SessionInfo(userId, userName, session));
    }

    /** 移除会话 */
    public void leave(Long docId, String sessionId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                docSessions.remove(docId);
            }
        }
    }

    /** 获取文档的所有订阅者（除指定 sessionId） */
    public Map<String, SessionInfo> getSubscribers(Long docId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        if (sessions == null) return Collections.emptyMap();
        return sessions;
    }

    public int getOnlineCount(Long docId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        return sessions != null ? sessions.size() : 0;
    }

    public record SessionInfo(Long userId, String userName, WebSocketSession session) {}
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocPresenceService.java
git commit -m "feat: 在线状态服务 — 文档房间管理"
```

---

### Task 6: 权限检查服务 — DocPermissionService

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocPermissionService.java`

- [ ] **Step 1: 创建 DocPermissionService**

```java
package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.entity.SysDocCollaborator;
import com.zjl.collaboration.entity.SysDocShareLink;
import com.zjl.collaboration.mapper.SysDocCollaboratorMapper;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysDocShareLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocPermissionService {

    private final SysDocMapper docMapper;
    private final SysDocCollaboratorMapper collaboratorMapper;
    private final SysDocShareLinkMapper shareLinkMapper;

    public enum Permission { VIEW, COMMENT, EDIT }

    /** 检查当前用户对文档的权限 */
    public Permission checkPermission(Long docId, Long userId, Long deptId) {
        SysDoc doc = docMapper.selectById(docId);
        if (doc == null) return null;

        /** 创建者拥有 EDIT 权限 */
        if (userId != null && userId.equals(doc.getUpdatedBy())) {
            return Permission.EDIT;
        }

        /** 检查协作者表 — 用户级 */
        Long count = collaboratorMapper.selectCount(new LambdaQueryWrapper<SysDocCollaborator>()
            .eq(SysDocCollaborator::getDocId, docId)
            .eq(SysDocCollaborator::getDeleted, 0)
            .eq(SysDocCollaborator::getTargetType, "USER")
            .eq(SysDocCollaborator::getTargetId, userId));
        if (count != null && count > 0) {
            SysDocCollaborator collab = collaboratorMapper.selectOne(new LambdaQueryWrapper<SysDocCollaborator>()
                .eq(SysDocCollaborator::getDocId, docId)
                .eq(SysDocCollaborator::getDeleted, 0)
                .eq(SysDocCollaborator::getTargetType, "USER")
                .eq(SysDocCollaborator::getTargetId, userId)
                .last("LIMIT 1"));
            return Permission.valueOf(collab.getPermission());
        }

        /** 检查协作者表 — 部门级 */
        if (deptId != null) {
            Long deptCount = collaboratorMapper.selectCount(new LambdaQueryWrapper<SysDocCollaborator>()
                .eq(SysDocCollaborator::getDocId, docId)
                .eq(SysDocCollaborator::getDeleted, 0)
                .eq(SysDocCollaborator::getTargetType, "DEPT")
                .eq(SysDocCollaborator::getTargetId, deptId));
            if (deptCount != null && deptCount > 0) {
                SysDocCollaborator deptCollab = collaboratorMapper.selectOne(new LambdaQueryWrapper<SysDocCollaborator>()
                    .eq(SysDocCollaborator::getDocId, docId)
                    .eq(SysDocCollaborator::getDeleted, 0)
                    .eq(SysDocCollaborator::getTargetType, "DEPT")
                    .eq(SysDocCollaborator::getTargetId, deptId)
                    .last("LIMIT 1"));
                return Permission.valueOf(deptCollab.getPermission());
            }
        }

        return null;
    }

    /** 通过分享 token 检查权限 */
    public Permission checkShareToken(String token) {
        SysDocShareLink link = shareLinkMapper.selectOne(new LambdaQueryWrapper<SysDocShareLink>()
            .eq(SysDocShareLink::getToken, token)
            .eq(SysDocShareLink::getDeleted, 0));
        if (link == null) return null;
        if (link.getExpiredAt() != null && link.getExpiredAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return Permission.valueOf(link.getPermission());
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocPermissionService.java
git commit -m "feat: 权限检查服务 — 创建者/协作者/分享链接三级权限"
```

---

### Task 7: WebSocket 处理器 — DocWebSocketHandler

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocWebSocketHandler.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/config/WebSocketConfig.java`

- [ ] **Step 1: 创建 DocWebSocketHandler**

```java
package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocPermissionService;
import com.zjl.collaboration.service.DocPresenceService;
import com.zjl.collaboration.service.DocPermissionService.Permission;
import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocOTService docOTService;
    private final DocPresenceService presenceService;
    private final DocPermissionService permissionService;
    private final JwtUtil jwtUtil;

    private final Map<String, UserContext> sessionUsers = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            String query = uri.getQuery();
            String token = null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
            if (token == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            Claims claims = jwtUtil.parse(token);
            Long userId = claims.get("userId", Long.class);
            String userName = claims.get("realName", String.class);
            sessionUsers.put(session.getId(), new UserContext(userId, userName));
        } catch (Exception e) {
            log.error("WebSocket 认证失败: session={}", session.getId(), e);
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode msg = OBJECT_MAPPER.readTree(message.getPayload());
            String action = msg.path("action").asText();
            UserContext user = sessionUsers.get(session.getId());
            if (user == null) {
                sendError(session, "未认证");
                return;
            }

            switch (action) {
                case "sub" -> handleSubscribe(session, msg, user);
                case "op" -> handleOperation(session, msg, user);
                case "cursor" -> handleCursor(session, msg, user);
                case "presence" -> handlePresence(session, msg, user);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
        }
    }

    private void handleSubscribe(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;

        Permission perm = permissionService.checkPermission(docId, user.userId, null);
        if (perm == null) {
            sendError(session, "无权访问此文档");
            return;
        }

        presenceService.join(docId, session.getId(), user.userId, user.userName, session);

        /** 发送文档当前版本 */
        DocOTService.DocSnapshot snapshot = docOTService.getDocument(docId);
        if (snapshot != null) {
            ObjectNode initMsg = OBJECT_MAPPER.createObjectNode();
            initMsg.put("action", "init");
            initMsg.put("docId", docId);
            initMsg.put("content", snapshot.content());
            initMsg.put("version", snapshot.version());
            initMsg.put("permission", perm.name());
            send(session, initMsg);
        }

        /** 广播在线状态 */
        broadcastPresence(docId, user.userId, user.userName, true, session.getId());
    }

    private void handleOperation(WebSocketSession session, JsonNode msg, UserContext user) throws Exception {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;

        int baseVersion = msg.path("version").asInt();
        JsonNode ops = msg.path("ops");

        if (!ops.isArray() || ops.isEmpty()) return;

        try {
            JsonNode transformedOps = docOTService.submitOperation(docId, user.userId, ops, baseVersion);

            /** ACK 给提交者 */
            ObjectNode ack = OBJECT_MAPPER.createObjectNode();
            ack.put("action", "ack");
            ack.put("docId", docId);
            ack.put("version", baseVersion + 1);
            send(session, ack);

            /** 广播给其他订阅者 */
            ObjectNode broadcast = OBJECT_MAPPER.createObjectNode();
            broadcast.put("action", "op");
            broadcast.put("docId", docId);
            broadcast.set("ops", transformedOps);
            broadcast.put("version", baseVersion + 1);
            broadcast.put("userId", user.userId);

            for (var entry : presenceService.getSubscribers(docId).entrySet()) {
                if (!entry.getKey().equals(session.getId())) {
                    send(entry.getValue().session(), broadcast);
                }
            }
        } catch (Exception e) {
            log.error("OT 操作处理失败: docId={}, version={}", docId, baseVersion, e);
            sendError(session, "操作冲突，请刷新页面");
        }
    }

    private void handleCursor(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;

        ObjectNode cursorMsg = OBJECT_MAPPER.createObjectNode();
        cursorMsg.put("action", "cursor");
        cursorMsg.put("docId", docId);
        cursorMsg.put("userId", user.userId);
        cursorMsg.put("userName", user.userName);
        cursorMsg.set("range", msg.path("range"));

        for (var entry : presenceService.getSubscribers(docId).entrySet()) {
            if (!entry.getKey().equals(session.getId())) {
                send(entry.getValue().session(), cursorMsg);
            }
        }
    }

    private void handlePresence(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;
        broadcastPresence(docId, user.userId, user.userName, msg.path("online").asBoolean(true), session.getId());
    }

    private void broadcastPresence(Long docId, Long userId, String userName, boolean online, String excludeSessionId) {
        ObjectNode presenceMsg = OBJECT_MAPPER.createObjectNode();
        presenceMsg.put("action", "presence");
        presenceMsg.put("docId", docId);
        presenceMsg.put("userId", userId);
        presenceMsg.put("userName", userName);
        presenceMsg.put("online", online);

        for (var entry : presenceService.getSubscribers(docId).entrySet()) {
            if (!entry.getKey().equals(excludeSessionId)) {
                send(entry.getValue().session(), presenceMsg);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UserContext user = sessionUsers.remove(session.getId());
        if (user == null) return;

        for (Long docId : presenceService.getSubscribers(0).keySet()) {
            presenceService.leave(docId, session.getId());
            broadcastPresence(docId, user.userId, user.userName, false, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误: session={}", session.getId(), exception);
    }

    private void send(WebSocketSession session, JsonNode message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("发送消息失败: session={}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, String errMsg) {
        try {
            ObjectNode error = OBJECT_MAPPER.createObjectNode();
            error.put("action", "error");
            error.put("message", errMsg);
            send(session, error);
        } catch (Exception ignored) {}
    }

    private record UserContext(Long userId, String userName) {}
}
```

`afterConnectionClosed` 方法有 bug — `presenceService.getSubscribers(0).keySet()` 不会返回文档列表。需要修正为追踪每个 session 订阅了哪些文档。

- [ ] **Step 2: 修正 DocPresenceService — 添加 session → docId 追踪**

在 `DocPresenceService.java` 添加：

```java
/** sessionId → 订阅的 docId 集合 */
private final ConcurrentHashMap<String, Set<Long>> sessionDocs = new ConcurrentHashMap<>();

/** 记录 session 订阅的文档 */
public void trackSubscription(String sessionId, Long docId) {
    sessionDocs.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(docId);
}

/** 获取 session 订阅的所有文档并清理 */
public Set<Long> removeSession(String sessionId) {
    Set<Long> docIds = sessionDocs.remove(sessionId);
    return docIds != null ? docIds : Collections.emptySet();
}
```

- [ ] **Step 3: 更新 DocWebSocketHandler.handleSubscribe — 添加追踪调用**

```java
// 在 handleSubscribe 的 presenceService.join(...) 之后添加：
presenceService.trackSubscription(session.getId(), docId);
```

- [ ] **Step 4: 修正 DocWebSocketHandler.afterConnectionClosed**

```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UserContext user = sessionUsers.remove(session.getId());
    if (user == null) return;

    Set<Long> docIds = presenceService.removeSession(session.getId());
    for (Long docId : docIds) {
        presenceService.leave(docId, session.getId());
        broadcastPresence(docId, user.userId, user.userName, false, session.getId());
    }
}
```

- [ ] **Step 5: 修改 WebSocketConfig — 注册 /ws/docs**

```java
// 在 WebSocketConfig.java 的 registerWebSocketHandlers 方法中添加：
registry.addHandler(docWebSocketHandler, "/ws/docs")
    .setAllowedOrigins("*");
```

- [ ] **Step 6: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocWebSocketHandler.java
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/DocPresenceService.java
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/config/WebSocketConfig.java
git commit -m "feat: 文档 WebSocket 处理器 — OT 协同/光标/在线状态"
```

---

### Task 8: 改造 DocController — 返回 version，接受 Delta

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocController.java`

- [ ] **Step 1: 重写 DocController**

```java
package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.common.entity.PageResult;
import com.zjl.common.entity.Result;
import com.zjl.common.entity.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final SysDocMapper docMapper;
    private final SysUserMapper userMapper;
    private final DocOTService docOTService;

    private static final String EMPTY_DELTA = "{\"ops\":[{\"insert\":\"\\n\"}]}";

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<SysDoc> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysDoc::getTitle, keyword);
        }
        wrapper.orderByDesc(SysDoc::getUpdatedAt);

        Page<SysDoc> pageResult = docMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = pageResult.getRecords().stream().map(doc -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", doc.getId());
            m.put("title", doc.getTitle());
            m.put("updatedBy", doc.getUpdatedBy());
            m.put("updatedByName", doc.getUpdatedByName());
            m.put("version", doc.getVersion());
            m.put("createdAt", doc.getCreatedAt());
            m.put("updatedAt", doc.getUpdatedAt());
            return m;
        }).toList();

        return Results.success(PageResult.of((int) pageResult.getCurrent(),
            (int) pageResult.getSize(), (int) pageResult.getTotal(), records));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        DocOTService.DocSnapshot snapshot = docOTService.getDocument(id);
        if (snapshot == null) {
            return Results.failure("404", "文档不存在");
        }
        SysDoc doc = docMapper.selectById(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("content", snapshot.content());
        m.put("version", snapshot.version());
        m.put("updatedBy", doc.getUpdatedBy());
        m.put("updatedByName", doc.getUpdatedByName());
        m.put("createdAt", doc.getCreatedAt());
        m.put("updatedAt", doc.getUpdatedAt());
        return Results.success(m);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody DocReq req,
                                               @RequestHeader("X-User-Id") Long userId) {
        SysDoc doc = new SysDoc();
        doc.setTitle(req.getTitle());
        doc.setContent(EMPTY_DELTA);
        doc.setVersion(0);
        doc.setSnapshotVersion(0);
        doc.setUpdatedBy(userId);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        var user = userMapper.selectById(userId);
        if (user != null) {
            doc.setUpdatedByName(user.getRealName());
        }

        docMapper.insert(doc);
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        return Results.success(m);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                                @RequestBody DocUpdateReq req) {
        SysDoc doc = docMapper.selectById(id);
        if (doc == null) return Results.failure("404", "文档不存在");
        if (req.getTitle() != null) doc.setTitle(req.getTitle());
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docMapper.deleteById(id);
        return Results.success();
    }

    @Data
    static class DocReq {
        private String title;
    }

    @Data
    static class DocUpdateReq {
        private String title;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocController.java
git commit -m "feat: 改造 DocController — 支持 Delta 内容/版本号/快照加载"
```

---

### Task 9: 评论控制器 — DocCommentController

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocCommentController.java`

- [ ] **Step 1: 创建 DocCommentController**

```java
package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.SysDocComment;
import com.zjl.collaboration.mapper.SysDocCommentMapper;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.common.entity.Result;
import com.zjl.common.entity.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocCommentController {

    private final SysDocCommentMapper commentMapper;
    private final SysUserMapper userMapper;

    @GetMapping("/{docId}/comments")
    public Result<List<Map<String, Object>>> list(@PathVariable Long docId) {
        List<SysDocComment> comments = commentMapper.selectList(
            new LambdaQueryWrapper<SysDocComment>()
                .eq(SysDocComment::getDocId, docId)
                .eq(SysDocComment::getDeleted, 0)
                .isNull(SysDocComment::getParentId)
                .orderByDesc(SysDocComment::getCreatedAt));

        Set<Long> userIds = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDocComment comment : comments) {
            userIds.add(comment.getUserId());
            Map<String, Object> m = commentToMap(comment);

            /** 加载回复 */
            List<SysDocComment> replies = commentMapper.selectList(
                new LambdaQueryWrapper<SysDocComment>()
                    .eq(SysDocComment::getDocId, docId)
                    .eq(SysDocComment::getParentId, comment.getId())
                    .eq(SysDocComment::getDeleted, 0)
                    .orderByAsc(SysDocComment::getCreatedAt));
            List<Map<String, Object>> replyList = new ArrayList<>();
            for (SysDocComment reply : replies) {
                userIds.add(reply.getUserId());
                replyList.add(commentToMap(reply));
            }
            m.put("replies", replyList);
            result.add(m);
        }

        /** 批量加载用户名 */
        if (!userIds.isEmpty()) {
            var users = userMapper.selectBatchIds(userIds);
            Map<Long, String> nameMap = users.stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getRealName(), (a, b) -> a));
            for (Map<String, Object> m : result) {
                m.put("userName", nameMap.getOrDefault(m.get("userId"), ""));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> replies = (List<Map<String, Object>>) m.get("replies");
                if (replies != null) {
                    for (Map<String, Object> r : replies) {
                        r.put("userName", nameMap.getOrDefault(r.get("userId"), ""));
                    }
                }
            }
        }

        return Results.success(result);
    }

    @PostMapping("/{docId}/comments")
    public Result<Map<String, Object>> create(@PathVariable Long docId,
                                               @RequestBody CommentReq req,
                                               @RequestHeader("X-User-Id") Long userId) {
        SysDocComment comment = new SysDocComment();
        comment.setDocId(docId);
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setAnchorIndex(req.getAnchorIndex());
        comment.setAnchorLength(req.getAnchorLength());
        comment.setParentId(req.getParentId());
        comment.setResolved(0);
        comment.setDeleted(0);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        Map<String, Object> m = commentToMap(comment);
        return Results.success(m);
    }

    @PutMapping("/comments/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CommentUpdateReq req) {
        SysDocComment comment = commentMapper.selectById(id);
        if (comment == null) return Results.failure("404", "评论不存在");
        if (req.getContent() != null) comment.setContent(req.getContent());
        if (req.getResolved() != null) comment.setResolved(req.getResolved());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        return Results.success();
    }

    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysDocComment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setDeleted(1);
            commentMapper.updateById(comment);
        }
        return Results.success();
    }

    private Map<String, Object> commentToMap(SysDocComment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("docId", c.getDocId());
        m.put("userId", c.getUserId());
        m.put("content", c.getContent());
        m.put("anchorIndex", c.getAnchorIndex());
        m.put("anchorLength", c.getAnchorLength());
        m.put("parentId", c.getParentId());
        m.put("resolved", c.getResolved());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    @Data
    static class CommentReq {
        private String content;
        private Integer anchorIndex;
        private Integer anchorLength;
        private Long parentId;
    }

    @Data
    static class CommentUpdateReq {
        private String content;
        private Integer resolved;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocCommentController.java
git commit -m "feat: 文档评论 API — 评论 CRUD + 回复 + 锚定"
```

---

### Task 10: 分享和协作者控制器 — DocShareController

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocShareController.java`

- [ ] **Step 1: 创建 DocShareController**

```java
package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocPermissionService;
import com.zjl.collaboration.service.DocPresenceService;
import com.zjl.common.entity.Result;
import com.zjl.common.entity.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocShareController {

    private final SysDocShareLinkMapper shareLinkMapper;
    private final SysDocCollaboratorMapper collaboratorMapper;
    private final SysDocMapper docMapper;
    private final SysUserMapper userMapper;
    private final DocPermissionService permissionService;
    private final DocOTService docOTService;
    private final DocPresenceService presenceService;

    /** ===== 协作者管理 ===== */

    @GetMapping("/docs/{docId}/collaborators")
    public Result<List<Map<String, Object>>> listCollaborators(@PathVariable Long docId) {
        List<SysDocCollaborator> list = collaboratorMapper.selectList(
            new LambdaQueryWrapper<SysDocCollaborator>()
                .eq(SysDocCollaborator::getDocId, docId)
                .eq(SysDocCollaborator::getDeleted, 0));

        Set<Long> userIds = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDocCollaborator c : list) {
            if ("USER".equals(c.getTargetType())) {
                userIds.add(c.getTargetId());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("docId", c.getDocId());
            m.put("targetType", c.getTargetType());
            m.put("targetId", c.getTargetId());
            m.put("permission", c.getPermission());
            m.put("online", false);
            result.add(m);
        }

        /** 批量填充用户名 */
        if (!userIds.isEmpty()) {
            var users = userMapper.selectBatchIds(userIds);
            Map<Long, String> nameMap = users.stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getRealName(), (a, b) -> a));
            for (Map<String, Object> m : result) {
                if ("USER".equals(m.get("targetType"))) {
                    m.put("targetName", nameMap.getOrDefault(m.get("targetId"), ""));
                }
            }
        }

        return Results.success(result);
    }

    @PostMapping("/docs/{docId}/collaborators")
    public Result<Map<String, Object>> addCollaborator(@PathVariable Long docId,
                                                        @RequestBody CollaboratorReq req) {
        SysDocCollaborator c = new SysDocCollaborator();
        c.setDocId(docId);
        c.setTargetType(req.getTargetType());
        c.setTargetId(req.getTargetId());
        c.setPermission(req.getPermission());
        c.setDeleted(0);
        c.setCreatedAt(LocalDateTime.now());
        collaboratorMapper.insert(c);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        return Results.success(m);
    }

    @PutMapping("/collaborators/{id}")
    public Result<Void> updateCollaborator(@PathVariable Long id,
                                            @RequestBody CollaboratorReq req) {
        SysDocCollaborator c = collaboratorMapper.selectById(id);
        if (c == null) return Results.failure("404", "协作者不存在");
        if (req.getPermission() != null) c.setPermission(req.getPermission());
        collaboratorMapper.updateById(c);
        return Results.success();
    }

    @DeleteMapping("/collaborators/{id}")
    public Result<Void> removeCollaborator(@PathVariable Long id) {
        SysDocCollaborator c = collaboratorMapper.selectById(id);
        if (c != null) { c.setDeleted(1); collaboratorMapper.updateById(c); }
        return Results.success();
    }

    /** ===== 分享链接管理 ===== */

    @GetMapping("/docs/{docId}/shares")
    public Result<List<Map<String, Object>>> listShares(@PathVariable Long docId) {
        List<SysDocShareLink> list = shareLinkMapper.selectList(
            new LambdaQueryWrapper<SysDocShareLink>()
                .eq(SysDocShareLink::getDocId, docId)
                .eq(SysDocShareLink::getDeleted, 0)
                .orderByDesc(SysDocShareLink::getCreatedAt));

        List<Map<String, Object>> result = list.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("token", s.getToken());
            m.put("permission", s.getPermission());
            m.put("expiredAt", s.getExpiredAt());
            m.put("createdAt", s.getCreatedAt());
            return m;
        }).toList();
        return Results.success(result);
    }

    @PostMapping("/docs/{docId}/shares")
    public Result<Map<String, Object>> createShare(@PathVariable Long docId,
                                                    @RequestBody ShareReq req) {
        SysDocShareLink link = new SysDocShareLink();
        link.setDocId(docId);
        link.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        link.setPermission(req.getPermission());
        link.setExpiredAt(req.getExpiredAt());
        link.setDeleted(0);
        link.setCreatedAt(LocalDateTime.now());
        shareLinkMapper.insert(link);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", link.getId());
        m.put("token", link.getToken());
        return Results.success(m);
    }

    @DeleteMapping("/shares/{id}")
    public Result<Void> deleteShare(@PathVariable Long id) {
        SysDocShareLink link = shareLinkMapper.selectById(id);
        if (link != null) { link.setDeleted(1); shareLinkMapper.updateById(link); }
        return Results.success();
    }

    @GetMapping("/docs/shared/{token}")
    public Result<Map<String, Object>> openByToken(@PathVariable String token) {
        DocPermissionService.Permission perm = permissionService.checkShareToken(token);
        if (perm == null) return Results.failure("403", "分享链接无效或已过期");

        SysDocShareLink link = shareLinkMapper.selectOne(
            new LambdaQueryWrapper<SysDocShareLink>()
                .eq(SysDocShareLink::getToken, token)
                .eq(SysDocShareLink::getDeleted, 0));

        DocOTService.DocSnapshot snapshot = docOTService.getDocument(link.getDocId());
        if (snapshot == null) return Results.failure("404", "文档不存在");

        SysDoc doc = docMapper.selectById(link.getDocId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("content", snapshot.content());
        m.put("version", snapshot.version());
        m.put("permission", perm.name());
        m.put("updatedByName", doc.getUpdatedByName());
        m.put("updatedAt", doc.getUpdatedAt());
        return Results.success(m);
    }

    @Data
    static class CollaboratorReq {
        private String targetType;
        private Long targetId;
        private String permission;
    }

    @Data
    static class ShareReq {
        private String permission;
        private LocalDateTime expiredAt;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocShareController.java
git commit -m "feat: 分享链接 + 协作者管理 API"
```

---

### Task 11: 网关路由 — 确认 WebSocket 透传和 /api/docs 路由

- [ ] **Step 1: 在网关中确认 `/api/docs/**` 已路由到 collaboration 服务**

检查 `enterprise-gateway-service/src/main/resources/application.yml`，确认 collaboration 路由中包含 `/api/docs/**`。根据之前的探索，已经包含，无需修改。

- [ ] **Step 2: 注意 WebSocket `/ws/docs` 不经过网关**

与 `/ws/chat` 相同模式，前端直接连接 `ws://host:8090/ws/docs`，不通过网关。无需配置网关 WebSocket 路由。

---

### Task 12: 前端 — 安装依赖

**Files:**
- Modify: `package.json` (根目录)

- [ ] **Step 1: 安装 npm 包**

```bash
cd /Users/zjl/projectByZhangjilin/EnterpriseKnowledgeWorkspace && npm install quill@^2.0.0 quill-cursors@^4.0.0
```

- [ ] **Step 2: 提交**

```bash
git add package.json package-lock.json
git commit -m "feat: 安装 Quill 编辑器 + quill-cursors 光标同步依赖"
```

---

### Task 13: 前端 — 重写 Documents.vue（Quill + WebSocket + 真实面板）

**Files:**
- Modify: `enterprise-web/src/pages/Documents.vue`

这是最大的单文件改造，用 Quill 替换 contenteditable，集成 WebSocket 协同，右侧面板接入真实 API。

- [ ] **Step 1: 重写 Documents.vue**

```vue
<template>
  <div class="doc-page">
    <!-- 左侧文档列表 -->
    <aside class="doc-sidebar">
      <div class="sidebar-header">
        <input v-model="keyword" placeholder="搜索文档" @input="searchDocs" class="search-input" />
        <el-button type="primary" size="small" @click="createDoc">新建</el-button>
      </div>
      <ul class="doc-list">
        <li v-for="doc in docs" :key="doc.id"
            :class="['doc-item', { active: currentDoc?.id === doc.id }]"
            @click="openDoc(doc)">
          <div class="doc-item-title">{{ doc.title || '无标题文档' }}</div>
          <div class="doc-item-meta">
            <span>{{ doc.updatedByName }}</span>
            <span>{{ formatTime(doc.updatedAt) }}</span>
          </div>
        </li>
      </ul>
    </aside>

    <!-- 中间编辑器 -->
    <main class="doc-editor">
      <template v-if="currentDoc">
        <div class="editor-toolbar" id="editor-toolbar"></div>
        <div class="editor-container" id="editor-container"></div>
        <div class="editor-footer">
          <span v-if="onlineCount > 1">{{ onlineCount }} 人在线</span>
          <span v-else>仅自己</span>
          <span class="save-status">{{ saveStatus }}</span>
        </div>
      </template>
      <div v-else class="editor-empty">选择或新建一个文档</div>
    </main>

    <!-- 右侧面板 -->
    <aside class="doc-panel" v-if="currentDoc">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="协作者" name="collaborators">
          <CollaboratorPanel :docId="currentDoc.id" />
        </el-tab-pane>
        <el-tab-pane label="评论" name="comments">
          <CommentPanel :docId="currentDoc.id" :quill="quill" />
        </el-tab-pane>
        <el-tab-pane label="分享" name="share">
          <SharePanel :docId="currentDoc.id" />
        </el-tab-pane>
      </el-tabs>
    </aside>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import Quill from 'quill'
import QuillCursors from 'quill-cursors'
import 'quill/dist/quill.snow.css'
import 'quill-cursors/dist/quill-cursors.css'
import { getAuthHeaders } from '../api'
import CollaboratorPanel from '../components/CollaboratorPanel.vue'
import CommentPanel from '../components/CommentPanel.vue'
import SharePanel from '../components/SharePanel.vue'

Quill.register('modules/cursors', QuillCursors)

const docs = ref([])
const currentDoc = ref(null)
const keyword = ref('')
const activeTab = ref('collaborators')
const saveStatus = ref('已保存')
const onlineCount = ref(1)

let quill = null
let ws = null
let cursors = null
let remoteChange = false
let localVersion = 0

/** 获取认证头 */
function authHeaders() {
  const h = getAuthHeaders()
  return {
    'X-User-Id': h['X-User-Id'] || '',
    'X-Department-Id': h['X-Department-Id'] || '',
    'X-Is-Admin': h['X-Is-Admin'] || 'false'
  }
}

function getToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.accessToken || ''
  } catch { return '' }
}

/** 加载文档列表 */
async function loadDocs() {
  try {
    const res = await fetch('/api/docs?keyword=' + encodeURIComponent(keyword.value))
    const body = await res.json()
    if (String(body.code) === '200') {
      docs.value = body.data?.records || []
    }
  } catch (e) {
    console.error('加载文档列表失败', e)
  }
}

/** 搜索防抖 */
let searchTimer = null
function searchDocs() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadDocs, 300)
}

/** 新建文档 */
async function createDoc() {
  const title = prompt('请输入文档标题')
  if (!title) return
  try {
    const res = await fetch('/api/docs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ title })
    })
    const body = await res.json()
    if (String(body.code) === '200') {
      await loadDocs()
      const newDoc = { id: body.data.id, title }
      openDoc(newDoc)
    }
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

/** 打开文档 */
async function openDoc(doc) {
  disconnectWs()

  try {
    const res = await fetch(`/api/docs/${doc.id}`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) !== '200') {
      ElMessage.error('加载文档失败')
      return
    }

    currentDoc.value = body.data
    localVersion = body.data.version || 0

    await nextTick()
    initEditor(body.data.content)
    connectWs(doc.id)

    /** 加载在线人数 */
    loadOnlineCount(doc.id)
  } catch (e) {
    console.error('打开文档失败', e)
  }
}

/** 初始化 Quill 编辑器 */
function initEditor(content) {
  const container = document.getElementById('editor-container')
  const toolbar = document.getElementById('editor-toolbar')
  if (!container) return

  if (quill) {
    quill.off('text-change')
  }

  quill = new Quill(container, {
    modules: {
      toolbar: toolbar || '#editor-toolbar',
      cursors: true
    },
    theme: 'snow',
    placeholder: '开始输入...'
  })

  cursors = quill.getModule('cursors')

  try {
    const delta = JSON.parse(content)
    quill.setContents(delta, 'silent')
  } catch (e) {
    quill.setContents([{ insert: '\n' }], 'silent')
  }

  quill.on('text-change', (delta, oldDelta, source) => {
    if (source !== 'user') return
    if (remoteChange) return

    /** 发送操作到服务端 */
    const ops = delta.ops
    if (!ops || ops.length === 0) return

    sendWsMessage({
      action: 'op',
      docId: currentDoc.value.id,
      ops: ops,
      version: localVersion
    })

    saveStatus.value = '保存中...'
  })

  quill.on('selection-change', (range) => {
    if (range && ws && ws.readyState === WebSocket.OPEN) {
      sendWsMessage({
        action: 'cursor',
        docId: currentDoc.value.id,
        range: range
      })
    }
  })
}

/** WebSocket 连接 */
function connectWs(docId) {
  const token = getToken()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = location.hostname
  ws = new WebSocket(`${protocol}//${host}:8090/ws/docs?token=${encodeURIComponent(token)}`)

  ws.onopen = () => {
    sendWsMessage({ action: 'sub', docId })
  }

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      handleWsMessage(msg)
    } catch (e) {
      console.error('解析 WebSocket 消息失败', e)
    }
  }

  ws.onclose = () => {
    console.log('WebSocket 断开')
  }

  ws.onerror = (e) => {
    console.error('WebSocket 错误', e)
  }
}

function disconnectWs() {
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
}

function sendWsMessage(msg) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg))
  }
}

function handleWsMessage(msg) {
  if (!currentDoc.value) return

  switch (msg.action) {
    case 'init': {
      localVersion = msg.version
      try {
        const delta = JSON.parse(msg.content)
        remoteChange = true
        quill.setContents(delta, 'silent')
        remoteChange = false
      } catch (e) { /* ignore */ }
      break
    }
    case 'ack': {
      localVersion = msg.version
      saveStatus.value = '已保存'
      break
    }
    case 'op': {
      localVersion = msg.version
      remoteChange = true
      quill.updateContents(msg.ops, 'silent')
      remoteChange = false
      break
    }
    case 'cursor': {
      if (cursors) {
        cursors.createCursor(msg.userId, msg.userName || ('用户' + msg.userId), 'red')
        cursors.moveCursor(msg.userId, msg.range)
      }
      break
    }
    case 'presence': {
      loadOnlineCount(currentDoc.value.id)
      break
    }
    case 'error': {
      ElMessage.error(msg.message || '协同编辑出错')
      break
    }
  }
}

async function loadOnlineCount(docId) {
  try {
    const res = await fetch(`/api/docs/${docId}/collaborators`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') {
      const list = body.data || []
      onlineCount.value = Math.max(1, list.filter(c => c.online).length)
    }
  } catch { /* ignore */ }
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onBeforeUnmount(() => {
  disconnectWs()
})

loadDocs()
</script>

<style scoped>
.doc-page { display: flex; height: 100vh; overflow: hidden; }
.doc-sidebar { width: 260px; border-right: 1px solid #e5e5e5; display: flex; flex-direction: column; background: #fff; }
.sidebar-header { padding: 12px; display: flex; gap: 8px; }
.search-input { flex: 1; border: 1px solid #ddd; border-radius: 4px; padding: 4px 8px; font-size: 13px; outline: none; }
.doc-list { flex: 1; overflow-y: auto; list-style: none; margin: 0; padding: 0; }
.doc-item { padding: 12px; cursor: pointer; border-bottom: 1px solid #f0f0f0; }
.doc-item:hover, .doc-item.active { background: #e8f0fe; }
.doc-item-title { font-size: 14px; font-weight: 500; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.doc-item-meta { font-size: 12px; color: #999; margin-top: 4px; display: flex; justify-content: space-between; }
.doc-editor { flex: 1; display: flex; flex-direction: column; background: #f9f9f9; min-width: 0; }
.editor-toolbar { border-bottom: 1px solid #ddd; background: #fff; }
.editor-container { flex: 1; padding: 24px 40px; overflow-y: auto; background: #fff; margin: 0 auto; width: 100%; max-width: 800px; }
.editor-container :deep(.ql-editor) { min-height: 400px; font-size: 15px; line-height: 1.8; }
.editor-footer { padding: 8px 16px; font-size: 12px; color: #999; border-top: 1px solid #eee; display: flex; justify-content: space-between; background: #fff; }
.editor-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 16px; }
.doc-panel { width: 300px; border-left: 1px solid #e5e5e5; background: #fff; overflow-y: auto; }
.doc-panel :deep(.el-tabs__header) { margin: 0; padding: 0 12px; }
.doc-panel :deep(.el-tabs__content) { padding: 0 12px; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-web/src/pages/Documents.vue
git commit -m "feat: 文档页面重写 — Quill 编辑器 + WebSocket 协同 + 光标同步"
```

---

### Task 14: 前端 — 评论面板组件

**Files:**
- Create: `enterprise-web/src/components/CommentPanel.vue`

- [ ] **Step 1: 创建 CommentPanel.vue**

```vue
<template>
  <div class="comment-panel">
    <div class="comment-input">
      <el-input v-model="newComment" type="textarea" :rows="2" placeholder="添加评论..." />
      <el-button type="primary" size="small" @click="addComment(null)" :disabled="!newComment.trim()" style="margin-top:8px">
        发表
      </el-button>
    </div>
    <div class="comment-list" v-if="comments.length > 0">
      <div v-for="c in comments" :key="c.id" class="comment-item" :class="{ resolved: c.resolved }">
        <div class="comment-user">{{ c.userName }}</div>
        <div class="comment-content">{{ c.content }}</div>
        <div class="comment-time">{{ formatTime(c.createdAt) }}</div>
        <div class="comment-actions">
          <el-button text size="small" @click="replyTo = c.id" v-if="replyTo !== c.id">回复</el-button>
          <el-button text size="small" @click="resolveComment(c)">{{ c.resolved ? '重新打开' : '解决' }}</el-button>
          <el-button text size="small" type="danger" @click="deleteComment(c.id)">删除</el-button>
        </div>
        <!-- 回复输入 -->
        <div v-if="replyTo === c.id" class="reply-input">
          <el-input v-model="replyContent" type="textarea" :rows="2" placeholder="回复..." />
          <el-button size="small" @click="addComment(c.id)" :disabled="!replyContent.trim()">回复</el-button>
          <el-button size="small" @click="replyTo = null">取消</el-button>
        </div>
        <!-- 回复列表 -->
        <div v-if="c.replies?.length" class="reply-list">
          <div v-for="r in c.replies" :key="r.id" class="reply-item">
            <span class="reply-user">{{ r.userName }}</span>: {{ r.content }}
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-hint">暂无评论</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthHeaders } from '../api'

const props = defineProps({ docId: Number, quill: Object })

const comments = ref([])
const newComment = ref('')
const replyTo = ref(null)
const replyContent = ref('')

function authHeaders() {
  const h = getAuthHeaders()
  return { 'Content-Type': 'application/json', 'X-User-Id': h['X-User-Id'] || '' }
}

async function loadComments() {
  if (!props.docId) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/comments`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') comments.value = body.data || []
  } catch { /* ignore */ }
}

async function addComment(parentId) {
  const content = parentId ? replyContent.value : newComment.value
  if (!content.trim()) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/comments`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ content: content.trim(), parentId })
    })
    const body = await res.json()
    if (String(body.code) === '200') {
      if (parentId) { replyTo.value = null; replyContent.value = '' }
      else newComment.value = ''
      await loadComments()
    }
  } catch { ElMessage.error('评论失败') }
}

async function resolveComment(c) {
  try {
    await fetch(`/api/docs/comments/${c.id}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify({ resolved: c.resolved ? 0 : 1 })
    })
    await loadComments()
  } catch { /* ignore */ }
}

async function deleteComment(id) {
  try {
    await fetch(`/api/docs/comments/${id}`, { method: 'DELETE', headers: authHeaders() })
    await loadComments()
  } catch { /* ignore */ }
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

watch(() => props.docId, loadComments, { immediate: true })
</script>

<style scoped>
.comment-panel { padding: 8px 0; }
.comment-input { margin-bottom: 16px; }
.comment-list { display: flex; flex-direction: column; gap: 12px; }
.comment-item { padding: 8px; background: #f8f8f8; border-radius: 6px; }
.comment-item.resolved { opacity: 0.5; }
.comment-user { font-size: 13px; font-weight: 600; color: #333; }
.comment-content { font-size: 13px; color: #555; margin: 4px 0; }
.comment-time { font-size: 11px; color: #aaa; }
.comment-actions { display: flex; gap: 4px; margin-top: 4px; }
.reply-input { margin-top: 8px; display: flex; gap: 4px; align-items: center; }
.reply-list { margin-top: 8px; padding-left: 12px; border-left: 2px solid #e0e0e0; }
.reply-item { font-size: 13px; color: #555; padding: 2px 0; }
.reply-user { font-weight: 600; color: #333; }
.empty-hint { text-align: center; color: #999; font-size: 13px; padding: 24px 0; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-web/src/components/CommentPanel.vue
git commit -m "feat: 评论面板组件 — 评论 CRUD + 回复 + 解决"
```

---

### Task 15: 前端 — 协作者面板 + 分享面板组件

**Files:**
- Create: `enterprise-web/src/components/CollaboratorPanel.vue`
- Create: `enterprise-web/src/components/SharePanel.vue`

- [ ] **Step 1: 创建 CollaboratorPanel.vue**

```vue
<template>
  <div class="collaborator-panel">
    <div class="add-collaborator">
      <el-input v-model="userId" placeholder="用户 ID" size="small" />
      <el-select v-model="permission" size="small" style="width:90px">
        <el-option label="查看" value="VIEW" />
        <el-option label="评论" value="COMMENT" />
        <el-option label="编辑" value="EDIT" />
      </el-select>
      <el-button type="primary" size="small" @click="addCollab" :disabled="!userId.trim()">添加</el-button>
    </div>
    <div class="collab-list" v-if="collaborators.length > 0">
      <div v-for="c in collaborators" :key="c.id" class="collab-item">
        <span class="collab-name">{{ c.targetName || ('用户' + c.targetId) }}</span>
        <span class="collab-perm">{{ c.permission }}</span>
        <el-button text size="small" type="danger" @click="removeCollab(c.id)">移除</el-button>
      </div>
    </div>
    <div v-else class="empty-hint">暂无协作者</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getAuthHeaders } from '../api'

const props = defineProps({ docId: Number })

const collaborators = ref([])
const userId = ref('')
const permission = ref('EDIT')

function authHeaders() {
  const h = getAuthHeaders()
  return { 'Content-Type': 'application/json', 'X-User-Id': h['X-User-Id'] || '' }
}

async function loadCollabs() {
  if (!props.docId) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/collaborators`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') collaborators.value = body.data || []
  } catch { /* ignore */ }
}

async function addCollab() {
  if (!userId.value.trim()) return
  try {
    await fetch(`/api/docs/${props.docId}/collaborators`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ targetType: 'USER', targetId: Number(userId.value.trim()), permission: permission.value })
    })
    userId.value = ''
    await loadCollabs()
  } catch { /* ignore */ }
}

async function removeCollab(id) {
  try {
    await fetch(`/api/collaborators/${id}`, { method: 'DELETE', headers: authHeaders() })
    await loadCollabs()
  } catch { /* ignore */ }
}

watch(() => props.docId, loadCollabs, { immediate: true })
</script>

<style scoped>
.collaborator-panel { padding: 8px 0; }
.add-collaborator { display: flex; gap: 4px; margin-bottom: 12px; }
.collab-list { display: flex; flex-direction: column; gap: 8px; }
.collab-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: #f8f8f8; border-radius: 4px; }
.collab-name { flex: 1; font-size: 13px; }
.collab-perm { font-size: 11px; color: #999; background: #eee; padding: 2px 6px; border-radius: 3px; }
.empty-hint { text-align: center; color: #999; font-size: 13px; padding: 24px 0; }
</style>
```

- [ ] **Step 2: 创建 SharePanel.vue**

```vue
<template>
  <div class="share-panel">
    <div class="create-share">
      <el-select v-model="perm" size="small" style="width:100px">
        <el-option label="查看" value="VIEW" />
        <el-option label="评论" value="COMMENT" />
        <el-option label="编辑" value="EDIT" />
      </el-select>
      <el-button type="primary" size="small" @click="createShare">生成链接</el-button>
    </div>
    <div class="share-list" v-if="shares.length > 0">
      <div v-for="s in shares" :key="s.id" class="share-item">
        <div class="share-link">
          <code>{{ shareUrl(s.token) }}</code>
        </div>
        <div class="share-meta">
          <span>{{ s.permission }}</span>
          <el-button text size="small" @click="copyLink(s.token)">复制</el-button>
          <el-button text size="small" type="danger" @click="deleteShare(s.id)">删除</el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-hint">暂无分享链接</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthHeaders } from '../api'

const props = defineProps({ docId: Number })

const shares = ref([])
const perm = ref('VIEW')

function authHeaders() {
  const h = getAuthHeaders()
  return { 'Content-Type': 'application/json', 'X-User-Id': h['X-User-Id'] || '' }
}

async function loadShares() {
  if (!props.docId) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/shares`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') shares.value = body.data || []
  } catch { /* ignore */ }
}

async function createShare() {
  try {
    const res = await fetch(`/api/docs/${props.docId}/shares`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ permission: perm.value })
    })
    const body = await res.json()
    if (String(body.code) === '200') {
      await loadShares()
    }
  } catch { ElMessage.error('创建失败') }
}

async function deleteShare(id) {
  try {
    await fetch(`/api/shares/${id}`, { method: 'DELETE', headers: authHeaders() })
    await loadShares()
  } catch { /* ignore */ }
}

function shareUrl(token) {
  return `${location.origin}/documents/shared/${token}`
}

function copyLink(token) {
  navigator.clipboard.writeText(shareUrl(token)).then(() => ElMessage.success('链接已复制'))
}

watch(() => props.docId, loadShares, { immediate: true })
</script>

<style scoped>
.share-panel { padding: 8px 0; }
.create-share { display: flex; gap: 4px; margin-bottom: 12px; }
.share-list { display: flex; flex-direction: column; gap: 8px; }
.share-item { padding: 8px; background: #f8f8f8; border-radius: 4px; }
.share-link code { font-size: 11px; word-break: break-all; color: #666; }
.share-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: 12px; color: #999; }
.empty-hint { text-align: center; color: #999; font-size: 13px; padding: 24px 0; }
</style>
```

- [ ] **Step 3: 提交**

```bash
git add enterprise-web/src/components/CollaboratorPanel.vue enterprise-web/src/components/SharePanel.vue
git commit -m "feat: 协作者面板 + 分享面板组件"
```

---

### Task 16: 数据库迁移文件补充 — 添加 created_by 字段

- [ ] **Step 1: 更新 003-doc-collaboration.sql 补充 created_by**

```sql
-- sys_doc 补充 created_by 字段（如果不存在）
ALTER TABLE sys_doc
  ADD COLUMN IF NOT EXISTS created_by BIGINT DEFAULT NULL COMMENT '创建者';
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-collaboration-service/src/main/resources/db/migration/003-doc-collaboration.sql
git commit -m "fix: sys_doc 补充 created_by 字段"
```

---

## 验证步骤

### 后端验证

```bash
# 启动协作服务
mvn spring-boot:run -pl enterprise-collaboration-service

# 验证 REST API
curl -H "X-User-Id: 1" http://localhost:8090/api/docs          # 文档列表
curl -H "X-User-Id: 1" http://localhost:8090/api/docs/1        # 文档详情（含 version）

# 验证 WebSocket（可以用 wscat 测试）
wscat -c "ws://localhost:8090/ws/docs?token=YOUR_JWT_TOKEN"
# > {"action":"sub","docId":1}
# > {"action":"op","docId":1,"ops":[{"insert":"Hello"}],"version":0}
```

### 前端验证

```bash
# 启动前端
cd enterprise-web && npm run dev

# 浏览器访问
open http://localhost:5173/documents
```

1. 创建新文档 → Quill 编辑器出现
2. 输入文字 → 自动通过 WebSocket 同步
3. 打开第二个浏览器 tab → 同时编辑同一文档，观察实时同步和光标
4. 右侧面板切到"评论" → 添加评论
5. 右侧面板切到"分享" → 生成分享链接，用隐身窗口打开

---
