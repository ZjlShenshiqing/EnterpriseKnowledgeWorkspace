# 关键词映射系统设计

## 背景

`KeywordMappings.vue` 页面当前使用 3 条硬编码 mock 数据，无后端 API。需要一个独立的关键词→知识库映射系统，与意图树系统无关。

## 方案

新建数据库表 + 标准 CRUD 后端 + 前端对接真实 API，去掉 mock。

## 数据库

新建表 `kb_keyword_mapping`：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键，自增 |
| keyword | varchar(100) | 关键词 |
| kb_name | varchar(100) | 目标知识库名称 |
| priority | int | 优先级（越大越靠前） |
| strategy | varchar(255) | 匹配策略说明 |
| enabled | tinyint(1) | 是否启用，默认 1 |
| created_at | datetime | |
| updated_at | datetime | |

使用 Flyway 迁移脚本创建。

## 后端 API（协作服务）

所有端点挂在 `/api/keyword-mappings` 下：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/keyword-mappings` | 分页列表，支持 keyword 搜索 |
| POST | `/api/keyword-mappings` | 新增一条映射 |
| PUT | `/api/keyword-mappings/{id}` | 编辑一条映射 |
| DELETE | `/api/keyword-mappings/{id}` | 删除一条映射（物理删除） |
| POST | `/api/keyword-mappings/match` | 匹配查询 |

**POST /match 请求体：**
```json
{ "query": "我要报销差旅费" }
```

**POST /match 响应体：**
```json
{
  "query": "我要报销差旅费",
  "hits": [
    { "id": 1, "keyword": "报销", "kb_name": "制度知识库", "priority": 100, "strategy": "优先返回制度类来源" },
    { "id": 3, "keyword": "差旅", "kb_name": "流程知识库", "priority": 80, "strategy": "返回预约流程与注意事项" }
  ]
}
```

匹配规则：遍历所有 `enabled=1` 的映射，筛选 `query.contains(keyword)` 的命中，按 priority 降序排列。

### 文件清单

- 新建：`KbKeywordMapping.java` — 实体类
- 新建：`KbKeywordMappingMapper.java` — MyBatis-Plus Mapper
- 新建：`KeywordMappingController.java` — REST 控制器
- 新建：`KeywordMappingService.java` — 业务逻辑（含 match）
- 新建：Flyway 迁移脚本 `V006__keyword_mapping.sql`

## 前端

修改 `KeywordMappings.vue`：
- 删除 3 条 mock 数据
- 页面加载时调 `GET /api/keyword-mappings` 获取列表
- 「新增映射」按钮 → 弹窗表单（keyword, kb_name, priority, strategy）
- 每行增加「编辑」「删除」操作按钮
- 保留现有表格列：关键词、知识库、优先级、策略
- 支持关键词搜索

## 变更范围

| 模块 | 变更 |
|------|------|
| 协作服务 | 新建实体 + Mapper + Controller + Flyway 迁移 |
| 前端 | KeywordMappings.vue 全面对接 API |
| 网关 | `/api/keyword-mappings/**` 路由到协作服务 |

## 不变

- 意图树系统（IntentController / IntentService）完全不动
- AgentLoop 不动（后续可集成 match 接口，但不在本次范围）
