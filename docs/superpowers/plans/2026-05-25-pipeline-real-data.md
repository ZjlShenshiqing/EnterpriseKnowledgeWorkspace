# 流水线管理/流水线任务真实数据化 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将流水线管理页和流水线任务页从硬编码数据改为真实后端数据，新增 `kb_pipeline` 表和配套 API。

**Architecture:** 新增 `kb_pipeline` 表与知识库一一对应，知识库创建/更新/删除时通过事件机制自动同步流水线记录。流水线任务页直接查询 `kb_document_chunk_log` 表，关联文档名和流水线名。

**Tech Stack:** Java 17, Spring Boot 3.3.5, MyBatis-Plus, MySQL, Vue 3 + Element Plus + Axios

---

## File Structure

```
enterprise-knowledge-ai-service/
  src/main/java/com/zjl/knowledge/
    entity/KbPipeline.java                          (NEW - 流水线实体)
    mapper/KbPipelineMapper.java                    (NEW - Mapper 接口)
    mapper/KbDocumentChunkLogMapper.java            (MODIFY - 新增分页查询方法)
    dto/pipeline/
      PipelineVO.java                               (NEW - 流水线响应)
      PipelineTaskVO.java                           (NEW - 任务响应)
    event/
      KnowledgeBaseCreatedEvent.java                (NEW - 知识库创建事件)
      KnowledgeBaseUpdatedEvent.java                (NEW - 知识库更新事件)
      KnowledgeBaseDeletedEvent.java                (NEW - 知识库删除事件)
      PipelineEventListener.java                    (NEW - 事件监听器)
    service/
      PipelineService.java                          (NEW - 接口)
      impl/PipelineServiceImpl.java                 (NEW - 实现)
    web/PipelineController.java                     (NEW - Controller)
  src/main/resources/mapper/
    KbPipelineMapper.xml                            (NEW - SQL 映射)
    KbDocumentChunkLogMapper.xml                    (MODIFY - 新增分页查询)

enterprise-knowledge-ai-service (现有文件修改):
  service/impl/KbKnowledgeBaseServiceImpl.java      (MODIFY - 发布事件)
  web/KbKnowledgeBaseController.java                (MODIFY - 删除时清理流水线)

enterprise-web/
  src/api/index.js                                  (MODIFY - 新增 API 函数)
  src/pages/admin/PipelineManage.vue                (MODIFY - 接入真实数据)
  src/pages/admin/PipelineTasks.vue                 (MODIFY - 接入真实数据)

database:
  需要执行 SQL 建表 + 为已有知识库回填流水线记录
```

---

### Task 1: 数据库 — 创建 `kb_pipeline` 表并回填已有知识库

**Files:**
- Create: `data/migrations/2026-05-25-add-kb-pipeline.sql`

- [ ] **Step 1: 编写建表 + 回填 SQL**

```sql
-- 创建 kb_pipeline 表
CREATE TABLE IF NOT EXISTS kb_pipeline (
    id               BIGINT        NOT NULL COMMENT '流水线 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '知识库 ID',
    name             VARCHAR(128)  NOT NULL COMMENT '流水线名称',
    description      VARCHAR(512)  DEFAULT '' COMMENT '描述',
    stages           JSON          COMMENT '处理阶段列表',
    chunk_strategy   VARCHAR(64)   DEFAULT '' COMMENT '分块策略',
    vector_enabled   TINYINT(1)    DEFAULT 0 COMMENT '是否启用向量写入',
    embedding_model  VARCHAR(128)  DEFAULT '' COMMENT '嵌入模型名称',
    status           VARCHAR(32)   DEFAULT 'ACTIVE' COMMENT '状态',
    created_at       DATETIME      NOT NULL COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL COMMENT '更新时间',
    deleted          TINYINT(1)    DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_kb_id (knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线定义表';

-- 为已有知识库回填流水线记录
INSERT INTO kb_pipeline (id, knowledge_base_id, name, description, stages,
                         chunk_strategy, vector_enabled, embedding_model,
                         status, created_at, updated_at, deleted)
SELECT
    kb.id,
    kb.id,
    CONCAT(kb.name, ' · 文档入库链路'),
    '覆盖上传、解析、分块、向量写入和主表回写',
    JSON_ARRAY('上传', '解析', '分块', '向量写入', '回写'),
    'PARAGRAPH',
    CASE WHEN kb.embedding_model IS NOT NULL AND kb.embedding_model != '' THEN 1 ELSE 0 END,
    COALESCE(kb.embedding_model, ''),
    'ACTIVE',
    kb.created_at,
    kb.updated_at,
    0
FROM kb_knowledge_base kb
WHERE kb.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM kb_pipeline p WHERE p.knowledge_base_id = kb.id
  );
```

- [ ] **Step 2: 执行 SQL**

Run: 在 MySQL 中执行上述 SQL 脚本，或通过应用的数据库管理工具执行。

- [ ] **Step 3: 验证**

Run: `SELECT COUNT(*) FROM kb_pipeline;` — 应与 `SELECT COUNT(*) FROM kb_knowledge_base WHERE deleted = 0;` 结果一致。

- [ ] **Step 4: Commit**

```bash
git add data/migrations/2026-05-25-add-kb-pipeline.sql
git commit -m "feat: 新增 kb_pipeline 表并回填已有知识库"
```

---

### Task 2: 后端 — 新增 KbPipeline 实体

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/entity/KbPipeline.java`

- [ ] **Step 1: 编写实体类**

```java
package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线定义 — 与知识库一一对应。
 */
@Data
@TableName("kb_pipeline")
public class KbPipeline {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long knowledgeBaseId;

    private String name;

    private String description;

    private String stages;

    private String chunkStrategy;

    private Integer vectorEnabled;

    private String embeddingModel;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/entity/KbPipeline.java
git commit -m "feat: 新增 KbPipeline 实体"
```

---

### Task 3: 后端 — 新增 KbPipelineMapper

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/mapper/KbPipelineMapper.java`
- Create: `enterprise-knowledge-ai-service/src/main/resources/mapper/KbPipelineMapper.xml`

- [ ] **Step 1: 编写 Mapper 接口**

```java
package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbPipeline;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流水线定义 Mapper。
 */
@Mapper
public interface KbPipelineMapper extends BaseMapper<KbPipeline> {
}
```

- [ ] **Step 2: 编写 XML 映射（空文件，预留给后续自定义查询）**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zjl.knowledge.mapper.KbPipelineMapper">
</mapper>
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/mapper/KbPipelineMapper.java \
        enterprise-knowledge-ai-service/src/main/resources/mapper/KbPipelineMapper.xml
git commit -m "feat: 新增 KbPipelineMapper"
```

---

### Task 4: 后端 — 新增 DTO（PipelineVO, PipelineTaskVO）

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/pipeline/PipelineVO.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/pipeline/PipelineTaskVO.java`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/pipeline
```

- [ ] **Step 2: 编写 PipelineVO**

```java
package com.zjl.knowledge.dto.pipeline;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流水线定义视图。
 */
@Data
public class PipelineVO {

    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String description;
    private List<String> stages;
    private String chunkStrategy;
    private Boolean vectorEnabled;
    private String embeddingModel;
    private String status;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 编写 PipelineTaskVO**

```java
package com.zjl.knowledge.dto.pipeline;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线任务视图 — 映射 kb_document_chunk_log 记录。
 */
@Data
public class PipelineTaskVO {

    private String taskId;
    private String type;
    private String documentName;
    private Long pipelineId;
    private String pipelineName;
    private String progress;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/pipeline/
git commit -m "feat: 新增 PipelineVO 和 PipelineTaskVO"
```

---

### Task 5: 后端 — 新增事件类和监听器

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/KnowledgeBaseCreatedEvent.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/KnowledgeBaseUpdatedEvent.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/KnowledgeBaseDeletedEvent.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/PipelineEventListener.java`

- [ ] **Step 1: 编写事件 record**

```java
package com.zjl.knowledge.event;

/**
 * 知识库创建事件 — 触发流水线自动创建。
 */
public record KnowledgeBaseCreatedEvent(Long kbId) {
}
```

```java
package com.zjl.knowledge.event;

/**
 * 知识库更新事件 — 触发流水线配置同步。
 */
public record KnowledgeBaseUpdatedEvent(Long kbId) {
}
```

```java
package com.zjl.knowledge.event;

/**
 * 知识库删除事件 — 触发流水线软删除。
 */
public record KnowledgeBaseDeletedEvent(Long kbId) {
}
```

- [ ] **Step 2: 编写 PipelineEventListener**

```java
package com.zjl.knowledge.event;

import com.zjl.knowledge.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听知识库生命周期事件，同步维护流水线记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineEventListener {

    private final PipelineService pipelineService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKbCreated(KnowledgeBaseCreatedEvent event) {
        try {
            pipelineService.createPipelineForKb(event.kbId());
        } catch (Exception e) {
            log.error("自动创建流水线失败, kbId={}", event.kbId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKbUpdated(KnowledgeBaseUpdatedEvent event) {
        try {
            pipelineService.syncPipelineFromKb(event.kbId());
        } catch (Exception e) {
            log.error("同步流水线配置失败, kbId={}", event.kbId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKbDeleted(KnowledgeBaseDeletedEvent event) {
        try {
            pipelineService.deletePipelineByKbId(event.kbId());
        } catch (Exception e) {
            log.error("删除流水线失败, kbId={}", event.kbId(), e);
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/
git commit -m "feat: 新增知识库事件和流水线监听器"
```

---

### Task 6: 后端 — 新增 PipelineService

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/PipelineService.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/PipelineServiceImpl.java`

- [ ] **Step 1: 编写 Service 接口**

```java
package com.zjl.knowledge.service;

import com.zjl.knowledge.dto.pipeline.PipelineTaskVO;
import com.zjl.knowledge.dto.pipeline.PipelineVO;

import java.util.List;

/**
 * 流水线管理服务。
 */
public interface PipelineService {

    /**
     * 列出流水线定义，支持按知识库 ID 和状态筛选。
     */
    List<PipelineVO> listPipelines(Long knowledgeBaseId, String status);

    /**
     * 获取流水线详情。
     */
    PipelineVO getPipeline(Long id);

    /**
     * 为指定知识库创建流水线记录。
     */
    void createPipelineForKb(Long kbId);

    /**
     * 从知识库同步配置到流水线快照。
     */
    void syncPipelineFromKb(Long kbId);

    /**
     * 软删除知识库关联的流水线。
     */
    void deletePipelineByKbId(Long kbId);

    /**
     * 分页查询流水线任务（文档分块日志）。
     */
    List<PipelineTaskVO> listTasks(Long pipelineId, Long documentId, String status, int current, int size);

    /**
     * 查询流水线任务总数。
     */
    long countTasks(Long pipelineId, Long documentId, String status);
}
```

- [ ] **Step 2: 编写 Service 实现**

```java
package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.dto.pipeline.PipelineTaskVO;
import com.zjl.knowledge.dto.pipeline.PipelineVO;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.entity.KbPipeline;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import com.zjl.knowledge.mapper.KbPipelineMapper;
import com.zjl.knowledge.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KbPipelineMapper pipelineMapper;
    private final KbKnowledgeBaseMapper kbMapper;
    private final KbDocumentChunkLogMapper chunkLogMapper;
    private final KbDocumentMapper documentMapper;

    @Override
    public List<PipelineVO> listPipelines(Long knowledgeBaseId, String status) {
        var q = Wrappers.lambdaQuery(KbPipeline.class);
        if (knowledgeBaseId != null) {
            q.eq(KbPipeline::getKnowledgeBaseId, knowledgeBaseId);
        }
        if (StringUtils.hasText(status)) {
            q.eq(KbPipeline::getStatus, status);
        }
        q.orderByDesc(KbPipeline::getUpdatedAt);
        return pipelineMapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public PipelineVO getPipeline(Long id) {
        KbPipeline p = pipelineMapper.selectById(id);
        return p == null ? null : toVo(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPipelineForKb(Long kbId) {
        KbKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            return;
        }

        Long exists = pipelineMapper.selectCount(
                Wrappers.lambdaQuery(KbPipeline.class)
                        .eq(KbPipeline::getKnowledgeBaseId, kbId)
        );
        if (exists != null && exists > 0) {
            return;
        }

        KbPipeline p = buildPipeline(kb);
        pipelineMapper.insert(p);
        log.info("自动创建流水线: kbId={}, pipelineId={}", kbId, p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncPipelineFromKb(Long kbId) {
        KbKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            return;
        }

        KbPipeline p = pipelineMapper.selectOne(
                Wrappers.lambdaQuery(KbPipeline.class)
                        .eq(KbPipeline::getKnowledgeBaseId, kbId)
        );
        if (p == null) {
            return;
        }

        p.setName(kb.getName() + " · 文档入库链路");
        p.setEmbeddingModel(kb.getEmbeddingModel() == null ? "" : kb.getEmbeddingModel());
        p.setVectorEnabled(kb.getEmbeddingModel() != null && !kb.getEmbeddingModel().isEmpty() ? 1 : 0);
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        log.info("同步流水线配置: kbId={}, pipelineId={}", kbId, p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePipelineByKbId(Long kbId) {
        var q = Wrappers.lambdaUpdate(KbPipeline.class)
                .set(KbPipeline::getStatus, "DELETED")
                .eq(KbPipeline::getKnowledgeBaseId, kbId)
                .eq(KbPipeline::getDeleted, 0);
        int rows = pipelineMapper.delete(q);
        log.info("删除流水线: kbId={}, affectedRows={}", kbId, rows);
    }

    @Override
    public List<PipelineTaskVO> listTasks(Long pipelineId, Long documentId, String status, int current, int size) {
        var q = Wrappers.lambdaQuery(KbDocumentChunkLog.class);
        if (pipelineId != null) {
            q.eq(KbDocumentChunkLog::getPipelineId, String.valueOf(pipelineId));
        }
        if (StringUtils.hasText(status)) {
            q.eq(KbDocumentChunkLog::getStatus, status);
        }
        q.orderByDesc(KbDocumentChunkLog::getStartedAt);

        Page<KbDocumentChunkLog> page = new Page<>(current, size);
        var result = chunkLogMapper.selectPage(page, q);

        if (result.getRecords().isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查文档名
        List<Long> docIds = result.getRecords().stream()
                .map(KbDocumentChunkLog::getDocumentId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> docNameMap = documentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getTitle));

        // 批量查流水线名
        List<Long> pIds = result.getRecords().stream()
                .map(KbDocumentChunkLog::getPipelineId)
                .filter(pid -> pid != null && !pid.isEmpty())
                .map(Long::parseLong)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> pipelineNameMap = Collections.emptyMap();
        if (!pIds.isEmpty()) {
            pipelineNameMap = pipelineMapper.selectBatchIds(pIds).stream()
                    .collect(Collectors.toMap(KbPipeline::getId, KbPipeline::getName));
        }

        return result.getRecords().stream().map(log -> toTaskVo(log, docNameMap, pipelineNameMap))
                .collect(Collectors.toList());
    }

    @Override
    public long countTasks(Long pipelineId, Long documentId, String status) {
        var q = Wrappers.lambdaQuery(KbDocumentChunkLog.class);
        if (pipelineId != null) {
            q.eq(KbDocumentChunkLog::getPipelineId, String.valueOf(pipelineId));
        }
        if (StringUtils.hasText(status)) {
            q.eq(KbDocumentChunkLog::getStatus, status);
        }
        return chunkLogMapper.selectCount(q);
    }

    private KbPipeline buildPipeline(KbKnowledgeBase kb) {
        KbPipeline p = new KbPipeline();
        p.setId(null);
        p.setKnowledgeBaseId(kb.getId());
        p.setName(kb.getName() + " · 文档入库链路");
        p.setDescription("覆盖上传、解析、分块、向量写入和主表回写");
        p.setStages("[\"上传\", \"解析\", \"分块\", \"向量写入\", \"回写\"]");
        p.setChunkStrategy("PARAGRAPH");
        p.setVectorEnabled(kb.getEmbeddingModel() != null && !kb.getEmbeddingModel().isEmpty() ? 1 : 0);
        p.setEmbeddingModel(kb.getEmbeddingModel() == null ? "" : kb.getEmbeddingModel());
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p.setDeleted(0);
        return p;
    }

    private PipelineVO toVo(KbPipeline p) {
        PipelineVO vo = new PipelineVO();
        vo.setId(p.getId());
        vo.setKnowledgeBaseId(p.getKnowledgeBaseId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setStages(parseStages(p.getStages()));
        vo.setChunkStrategy(p.getChunkStrategy());
        vo.setVectorEnabled(p.getVectorEnabled() != null && p.getVectorEnabled() == 1);
        vo.setEmbeddingModel(p.getEmbeddingModel());
        vo.setStatus(p.getStatus());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    private PipelineTaskVO toTaskVo(KbDocumentChunkLog log, Map<Long, String> docNameMap,
                                     Map<Long, String> pipelineNameMap) {
        PipelineTaskVO vo = new PipelineTaskVO();
        vo.setTaskId(String.valueOf(log.getId()));
        vo.setType("文档分块");
        vo.setDocumentName(docNameMap.getOrDefault(log.getDocumentId(), "—"));
        String pid = log.getPipelineId();
        if (pid != null && !pid.isEmpty()) {
            try {
                vo.setPipelineId(Long.parseLong(pid));
            } catch (NumberFormatException ignored) {
                vo.setPipelineId(null);
            }
            vo.setPipelineName(pipelineNameMap.getOrDefault(vo.getPipelineId(), "—"));
        }
        vo.setProgress(resolveProgress(log.getStatus()));
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setCreatedAt(log.getStartedAt());
        vo.setUpdatedAt(log.getEndedAt());
        return vo;
    }

    private String resolveProgress(String status) {
        if ("SUCCESS".equals(status)) {
            return "100%";
        }
        if ("RUNNING".equals(status)) {
            return "处理中";
        }
        return "—";
    }

    private List<String> parseStages(String stagesJson) {
        if (!StringUtils.hasText(stagesJson)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(stagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 stages JSON 失败: {}", stagesJson, e);
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/PipelineService.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/PipelineServiceImpl.java
git commit -m "feat: 新增 PipelineService 及实现"
```

---

### Task 7: 后端 — 新增 PipelineController

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/PipelineController.java`

- [ ] **Step 1: 编写 Controller**

```java
package com.zjl.knowledge.web;

import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.dto.pipeline.PipelineTaskVO;
import com.zjl.knowledge.dto.pipeline.PipelineVO;
import com.zjl.knowledge.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流水线管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * 流水线列表。
     */
    @GetMapping
    public Result<List<PipelineVO>> list(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(required = false) String status
    ) {
        List<PipelineVO> list = pipelineService.listPipelines(knowledgeBaseId, status);
        return Results.success(list);
    }

    /**
     * 流水线详情。
     */
    @GetMapping("/{id}")
    public Result<PipelineVO> detail(@PathVariable Long id) {
        PipelineVO vo = pipelineService.getPipeline(id);
        return Results.success(vo);
    }

    /**
     * 流水线任务列表（分页）。
     */
    @GetMapping("/tasks")
    public Result<PageResult<PipelineTaskVO>> tasks(
            @RequestParam(required = false) Long pipelineId,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<PipelineTaskVO> records = pipelineService.listTasks(pipelineId, documentId, status, current, size);
        long total = pipelineService.countTasks(pipelineId, documentId, status);
        return Results.success(PageResult.of(current, size, total, records));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/PipelineController.java
git commit -m "feat: 新增 PipelineController"
```

---

### Task 8: 后端 — 修改 KbKnowledgeBaseServiceImpl 发布事件

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbKnowledgeBaseServiceImpl.java`

- [ ] **Step 1: 注入 ApplicationEventPublisher 并在 create/update/delete 中发布事件**

修改文件，在三处位置添加事件发布：

在类的字段声明处，添加：
```java
private final ApplicationEventPublisher applicationEventPublisher;
```

在 `create` 方法的 `return row.getId();` 之前添加：
```java
applicationEventPublisher.publishEvent(new KnowledgeBaseCreatedEvent(row.getId()));
```

在 `update` 方法的 `kbKnowledgeBaseMapper.updateById(kb);` 之后添加：
```java
applicationEventPublisher.publishEvent(new KnowledgeBaseUpdatedEvent(id));
```

在 `delete` 方法的 `kbKnowledgeBaseMapper.deleteById(id);` 之后添加：
```java
applicationEventPublisher.publishEvent(new KnowledgeBaseDeletedEvent(id));
```

同时在文件顶部添加 import：
```java
import com.zjl.knowledge.event.KnowledgeBaseCreatedEvent;
import com.zjl.knowledge.event.KnowledgeBaseDeletedEvent;
import com.zjl.knowledge.event.KnowledgeBaseUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbKnowledgeBaseServiceImpl.java
git commit -m "feat: 知识库生命周期事件发布"
```

---

### Task 9: 后端 — 验证编译

- [ ] **Step 1: 编译验证**

```bash
cd enterprise-knowledge-ai-service && mvn compile -q
```

Expected: BUILD SUCCESS，无编译错误。

注意：此步骤仅验证编译，不运行测试（项目测试依赖数据库和 Milvus 环境）。

- [ ] **Step 2: Commit（如有修正）**

---

### Task 10: 前端 — 新增 API 函数

**Files:**
- Modify: `enterprise-web/src/api/index.js`

- [ ] **Step 1: 在文件末尾添加流水线 API**

在 `index.js` 文件末尾添加：

```js
// ---- Pipelines ----

export function getPipelines(params) {
  return kbApi.get('/pipelines', { params })
}

export function getPipelineDetail(id) {
  return kbApi.get(`/pipelines/${id}`)
}

export function getPipelineTasks(params) {
  return kbApi.get('/pipelines/tasks', { params })
}
```

注意：`/api/pipelines` 路径不在已有的 `/api/kb` 前缀下，需要确认网关路由配置。如果网关只转发 `/api/kb/**` 到 knowledge-ai-service，需要在网关添加 `/api/pipelines/**` 的路由规则，或者将 Controller 的 `@RequestMapping` 改为 `/api/kb/pipelines`。

为保持简洁，这里将 Controller 路径挂到 `/api/kb` 下，API 调用改为：

```js
// ---- Pipelines ----

export function getPipelines(params) {
  return kbApi.get('/pipelines', { params })
}

export function getPipelineDetail(id) {
  return kbApi.get(`/pipelines/${id}`)
}

export function getPipelineTasks(params) {
  return kbApi.get('/pipelines/tasks', { params })
}
```

同时需要将后端 PipelineController 的 `@RequestMapping("/api/kb/pipelines")` 改为 `@RequestMapping("/api/kb/pipelines")`。

实际上，看前端 `kbApi` 的 baseURL 就是 `/api/kb`，所以后端的 Controller 应该映射到 `/api/kb/pipelines`。

- [ ] **Step 2: 同步修改后端 Controller 路径**

修改 `PipelineController.java` 中 `@RequestMapping("/api/kb/pipelines")` 为 `@RequestMapping("/api/kb/pipelines")`。

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/api/index.js \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/PipelineController.java
git commit -m "feat: 前端新增流水线 API + Controller 路径修正"
```

---

### Task 11: 前端 — PipelineManage.vue 接入真实数据

**Files:**
- Modify: `enterprise-web/src/pages/admin/PipelineManage.vue`

- [ ] **Step 1: 重写 script setup，接入 API**

将文件 `<script setup>` 部分替换为：

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPipelines } from '@/api'

const router = useRouter()
const pipelines = ref([])
const loading = ref(false)

const fetchPipelines = async () => {
  loading.value = true
  try {
    const res = await getPipelines()
    pipelines.value = res.data || []
  } catch (e) {
    ElMessage.error('获取流水线列表失败')
  } finally {
    loading.value = false
  }
}

const stageLabel = (stages) => {
  if (!stages || stages.length === 0) return '0 个'
  return stages.length + ' 个'
}

const statusType = (status) => {
  if (status === 'ACTIVE') return 'success'
  return 'info'
}

const statusLabel = (status) => {
  if (status === 'ACTIVE') return '运行中'
  return status
}

onMounted(fetchPipelines)
</script>
```

- [ ] **Step 2: 修改模板，从数据对象取值**

```vue
<template>
  <div class="admin-view" v-loading="loading">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">流水线管理</div>
        <div class="admin-page-subtitle">管理数据接入、解析、分块、向量写入等处理通道。</div>
      </div>
      <div class="admin-actions">
        <el-button plain @click="fetchPipelines">刷新</el-button>
        <el-button type="primary" @click="router.push('/admin/kb/bases')">知识库管理</el-button>
      </div>
    </section>

    <el-empty v-if="!loading && pipelines.length === 0" description="暂无流水线，请先创建知识库" />

    <section v-else class="admin-grid-3">
      <article v-for="pipeline in pipelines" :key="pipeline.id" class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">{{ pipeline.name }}</div>
            <div class="admin-section-hint">{{ pipeline.description }}</div>
          </div>
          <el-tag :type="statusType(pipeline.status)" size="small">{{ statusLabel(pipeline.status) }}</el-tag>
        </div>
        <div class="admin-note">{{ pipeline.description }}</div>
        <div class="admin-meta-list" style="margin-top: 18px;">
          <div class="admin-meta-row">
            <span class="admin-meta-label">分块策略</span>
            <span class="admin-meta-value">{{ pipeline.chunkStrategy }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">向量写入</span>
            <span class="admin-meta-value">{{ pipeline.vectorEnabled ? '已启用' : '未启用' }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">嵌入模型</span>
            <span class="admin-meta-value">{{ pipeline.embeddingModel || '全局默认' }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">处理阶段</span>
            <span class="admin-meta-value">{{ stageLabel(pipeline.stages) }}</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/admin/PipelineManage.vue
git commit -m "feat: PipelineManage 接入真实 API 数据"
```

---

### Task 12: 前端 — PipelineTasks.vue 接入真实数据

**Files:**
- Modify: `enterprise-web/src/pages/admin/PipelineTasks.vue`

- [ ] **Step 1: 重写 script setup，接入 API**

```vue
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPipelineTasks } from '@/api'

const tasks = ref([])
const loading = ref(false)
const pagination = reactive({ current: 1, size: 20, total: 0 })
const activeStatus = ref('')

const fetchTasks = async (page = 1) => {
  loading.value = true
  try {
    const params = { current: page, size: pagination.size }
    if (activeStatus.value) {
      params.status = activeStatus.value
    }
    const res = await getPipelineTasks(params)
    const data = res.data || {}
    tasks.value = data.records || []
    pagination.current = data.current || page
    pagination.total = data.total || 0
  } catch (e) {
    ElMessage.error('获取任务列表失败')
  } finally {
    loading.value = false
  }
}

const filterByStatus = (status) => {
  activeStatus.value = status
  fetchTasks(1)
}

const clearFilter = () => {
  activeStatus.value = ''
  fetchTasks(1)
}

const statusTagType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const onPageChange = (page) => {
  fetchTasks(page)
}

onMounted(() => fetchTasks())
</script>
```

- [ ] **Step 2: 修改模板**

```vue
<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">流水线任务</div>
        <div class="admin-page-subtitle">查看当前执行中的异步任务、重试情况和失败原因。</div>
      </div>
      <div class="admin-actions">
        <el-button plain @click="fetchTasks(pagination.current)">刷新队列</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">任务队列</div>
          <div class="admin-toolbar-subtitle">文档分块、向量同步等异步处理任务。</div>
        </div>
        <div class="admin-filters">
          <span class="admin-chip" :class="{ active: activeStatus === '' }" @click="clearFilter">全部</span>
          <span class="admin-chip" :class="{ active: activeStatus === 'RUNNING' }" @click="filterByStatus('RUNNING')">进行中</span>
          <span class="admin-chip" :class="{ active: activeStatus === 'FAILED' }" @click="filterByStatus('FAILED')">失败</span>
          <span class="admin-chip" :class="{ active: activeStatus === 'SUCCESS' }" @click="filterByStatus('SUCCESS')">成功</span>
        </div>
      </div>
      <el-table :data="tasks" v-loading="loading" style="width: 100%">
        <el-table-column prop="taskId" label="任务 ID" width="200" />
        <el-table-column prop="type" label="任务类型" min-width="120" />
        <el-table-column prop="documentName" label="关联文档" min-width="160" />
        <el-table-column prop="pipelineName" label="所属流水线" min-width="180" />
        <el-table-column prop="progress" label="进度" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="pagination.current"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </section>
  </div>
</template>
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/admin/PipelineTasks.vue
git commit -m "feat: PipelineTasks 接入真实 API 数据"
```

---

### Task 13: 端到端验证

- [ ] **Step 1: 启动后端服务**

```bash
mvn spring-boot:run -pl enterprise-knowledge-ai-service
```

- [ ] **Step 2: 用 curl 验证 API**

```bash
# 验证流水线列表
curl -s http://localhost:8081/api/kb/pipelines | python3 -m json.tool

# 验证流水线详情
curl -s http://localhost:8081/api/kb/pipelines/{id} | python3 -m json.tool

# 验证任务列表
curl -s "http://localhost:8081/api/kb/pipelines/tasks?current=1&size=10" | python3 -m json.tool
```

Expected: 三个接口均返回正确的 JSON 数据结构。

- [ ] **Step 3: 启动前端验证页面渲染**

```bash
cd enterprise-web && npm run dev
```

打开浏览器访问流水线管理页和流水线任务页，确认：
- 流水线管理页展示真实的知识库关联流水线
- 流水线任务页展示真实的分块执行记录
- loading / 空数据 / 分页 / 筛选均正常工作

- [ ] **Step 4: Commit（如有修正）**
```

---

## 自检清单

1. **Spec 覆盖**
   - 数据库建表 ✅ (Task 1)
   - 流水线 API GET /api/kb/pipelines ✅ (Task 7)
   - 流水线详情 GET /api/kb/pipelines/{id} ✅ (Task 7)
   - 流水线任务 GET /api/kb/pipelines/tasks ✅ (Task 7)
   - 知识库创建时自动生成流水线 ✅ (Task 5, 6, 8)
   - 知识库更新时同步流水线 ✅ (Task 5, 6, 8)
   - 知识库删除时软删除流水线 ✅ (Task 5, 6, 8)
   - 前端 API 函数 ✅ (Task 10)
   - PipelineManage.vue 改造 ✅ (Task 11)
   - PipelineTasks.vue 改造 ✅ (Task 12)

2. **无 Placeholder** — 无 TBD/TODO，所有步骤包含完整代码
3. **类型一致性** — PipelineVO/PipelineTaskVO 字段与前后端一致
4. **路由修正** — 发现设计文档中 API 路径与前端 kbApi baseURL 不匹配，已修正为 `/api/kb/pipelines`