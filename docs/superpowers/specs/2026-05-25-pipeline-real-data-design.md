# 流水线管理/流水线任务真实数据化 · 设计文档

## 背景

当前 `PipelineManage.vue` 和 `PipelineTasks.vue` 的数据完全硬编码在前端。后端虽有 `ProcessMode.PIPELINE` 枚举预留，但无任何流水线基础设施。本次设计让流水线管理/任务页展示真实数据。

## 范围

- 流水线管理页：每个知识库自动对应一条 `kb_pipeline` 记录，展示处理链路和配置快照
- 流水线任务页：展示 `kb_document_chunk_log` 表的真实分块执行记录
- 不做可编排的流水线引擎，不做自定义阶段配置

## 数据库

### 新表 `kb_pipeline`

```sql
CREATE TABLE kb_pipeline (
    id              BIGINT       PRIMARY KEY,
    knowledge_base_id BIGINT     NOT NULL,
    name            VARCHAR(128) NOT NULL COMMENT '流水线名称',
    description     VARCHAR(512) COMMENT '描述',
    stages          JSON         COMMENT '处理阶段列表',
    chunk_strategy  VARCHAR(64)  COMMENT '分块策略',
    vector_enabled  TINYINT(1)   DEFAULT 0 COMMENT '是否启用向量写入',
    embedding_model VARCHAR(128) COMMENT '嵌入模型名称',
    status          VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    deleted         TINYINT(1)   DEFAULT 0,
    INDEX idx_kb_id (knowledge_base_id)
) COMMENT '流水线定义表';
```

- 与 `kb_knowledge_base` 一一对应
- `stages` 为 JSON 数组，如 `["上传", "解析", "分块", "向量写入", "回写"]`
- 知识库创建时自动生成流水线记录，知识库配置变更时同步更新快照字段，知识库删除时软删除流水线

## 后端 API

### 流水线管理

**`GET /api/pipelines`** — 列表，支持 `knowledgeBaseId`、`status` 筛选

```json
{
  "code": 0,
  "data": [
    {
      "id": 1001,
      "knowledgeBaseId": 10,
      "name": "通用知识库 · 文档入库链路",
      "description": "覆盖上传、解析、分块、向量写入和主表回写",
      "stages": ["上传", "解析", "分块", "向量写入", "回写"],
      "chunkStrategy": "FIXED_SIZE",
      "vectorEnabled": true,
      "embeddingModel": "text-embedding-v3",
      "status": "ACTIVE",
      "updatedAt": "2026-05-25 10:00"
    }
  ]
}
```

**`GET /api/pipelines/{id}`** — 详情，含阶段详情和最近任务摘要

### 流水线任务

**`GET /api/pipeline-tasks`** — 查询 `kb_document_chunk_log`，支持 `pipelineId`、`documentId`、`status` 筛选，分页返回

```json
{
  "code": 0,
  "data": {
    "current": 1,
    "size": 20,
    "total": 42,
    "records": [
      {
        "taskId": "chunk-20260521-001",
        "type": "文档分块",
        "documentName": "产品手册v3.pdf",
        "pipelineId": 1001,
        "pipelineName": "通用知识库 · 文档入库链路",
        "progress": "82%",
        "status": "RUNNING",
        "errorMessage": null,
        "createdAt": "2026-05-21 09:30",
        "updatedAt": "2026-05-21 10:21"
      }
    ]
  }
}
```

### 生命周期

- 知识库创建 → `@TransactionalEventListener` 自动生成流水线记录
- 知识库配置更新 → 同步刷新流水线 `chunk_strategy` / `vector_enabled` / `embedding_model` / `stages`
- 知识库删除 → 软删除关联流水线

## 前端

### api/index.js

```js
export const getPipelines = (params) => request.get('/api/pipelines', { params })
export const getPipelineDetail = (id) => request.get(`/api/pipelines/${id}`)
export const getPipelineTasks = (params) => request.get('/api/pipeline-tasks', { params })
```

### PipelineManage.vue

- 页面加载调用 `getPipelines()`，替换硬编码数组
- "新增流水线"按钮改为跳转知识库创建页
- "查看模板"保留，展示预设阶段模板
- 增加 loading / 空数据 / 错误状态

### PipelineTasks.vue

- 页面加载调用 `getPipelineTasks()`，筛选 chip 可点击
- "刷新队列"重新请求，"重跑失败任务"调用已有文档重新分块接口
- 增加 loading / 空数据 / 错误状态

## 涉及模块

- `enterprise-knowledge-ai-service`：新增 Pipeline 实体、Mapper、Service、Controller
- `enterprise-web`：前端 API + 页面改动
- 不涉及 gateway / collaboration / workbench
