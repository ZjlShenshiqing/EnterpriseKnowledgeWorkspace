# 意图树配置系统

## 目标

为管理后台的意图树配置和意图列表页面提供完整后端支持和前端重写，实现意图层级管理、匹配规则配置、知识库绑定和实时匹配预览。

## 数据模型

### kb_intent_node — 意图节点表（支持任意层级树）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| parent_id | BIGINT NULL | 父节点ID，NULL为根场景 |
| name | VARCHAR(128) | 节点名称 |
| level | TINYINT | 1=场景 2=意图 |
| sort_order | INT | 同级排序 |
| description | VARCHAR(512) | 节点说明 |
| enabled | TINYINT | 是否启用 |
| created_at / updated_at | TIMESTAMP | 时间戳 |
| KEY | idx_parent | (parent_id) |

### kb_intent_rule — 意图规则表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| node_id | BIGINT | 意图节点ID |
| rule_type | VARCHAR(16) | keyword / regex |
| expression | VARCHAR(256) | 关键词或正则表达式 |
| weight | DOUBLE | 权重（默认1.0） |
| enabled | TINYINT | 是否启用 |
| created_at | TIMESTAMP | 创建时间 |
| KEY | idx_node | (node_id) |

### kb_intent_kb_rel — 意图知识库关联表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| node_id | BIGINT | 意图节点ID |
| kb_id | BIGINT | 知识库ID |
| weight | DOUBLE | 检索权重（默认1.0） |
| created_at | TIMESTAMP | 创建时间 |
| UNIQUE KEY | uk_node_kb | (node_id, kb_id) |

## API 设计

所有端点挂载在 collaboration-service（8082），路径前缀 `/api/intents`。通过网关路由，用户身份从 `X-User-Id` header 获取。

### 节点管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/intents/nodes` | 获取全量意图树 |
| GET | `/api/intents/nodes/{id}` | 获取单个节点详情（含规则+知识库） |
| POST | `/api/intents/nodes` | 新增节点 `{ parentId, name, level, description }` |
| PUT | `/api/intents/nodes/{id}` | 更新节点信息 |
| DELETE | `/api/intents/nodes/{id}` | 删除节点（级联删除子节点+规则+关联） |
| PUT | `/api/intents/nodes/{id}/sort` | 拖拽排序 `{ parentId, sortOrder }` |

### 规则管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/intents/nodes/{id}/rules` | 获取节点规则列表 |
| POST | `/api/intents/nodes/{id}/rules` | 新增规则 |
| PUT | `/api/intents/rules/{ruleId}` | 更新规则 |
| DELETE | `/api/intents/rules/{ruleId}` | 删除规则 |

### 知识库关联

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/intents/nodes/{id}/kbs` | 获取关联的知识库列表 |
| POST | `/api/intents/nodes/{id}/kbs` | 绑定知识库 `{ kbId, weight }` |
| PUT | `/api/intents/kb-rel/{relId}` | 调整权重 |
| DELETE | `/api/intents/kb-rel/{relId}` | 解除绑定 |

### 预览测试

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/intents/match` | 测试匹配 `{ query }` → 返回命中意图和规则 |

## 前端设计

### IntentConfig.vue — 意图树配置页

**布局：左侧 280px 树 + 右侧详情面板**

**左侧树区域：**
- 顶部"新增场景"按钮
- 树组件展示场景→意图结构
- 点击节点 → 右侧加载详情
- 支持展开/折叠、右键菜单（新增子节点、删除）
- 拖拽调整父子关系和排序

**右侧详情面板（选中节点后显示）：**

1. 节点信息区 — 编辑名称、层级、描述、启用状态，保存按钮
2. 匹配规则区 — 表格列表，每行：类型(keyword/regex)、表达式、权重、开关、删除。顶部新增按钮弹出表单
3. 关联知识库区 — 表格列表，每行：知识库名、权重、解绑。顶部下拉选择+权重+绑定按钮
4. 匹配预览区 — 输入框+测试按钮，显示命中结果（意图名、命中规则、权重）

**未选中节点时右侧显示空状态提示"选择一个节点查看详情"。**

### IntentList.vue — 意图列表页

保留现有表格布局，改为从 API 获取数据。表格列：意图名称、所属场景、关联知识库、规则数、状态、最近更新时间。支持按场景筛选。

## 后端组件

| 组件 | 位置 | 职责 |
|------|------|------|
| KbIntentNode entity | entity/ | 节点实体 |
| KbIntentRule entity | entity/ | 规则实体 |
| KbIntentKbRel entity | entity/ | 关联实体 |
| KbIntentNodeMapper | mapper/ | 节点 Mapper |
| KbIntentRuleMapper | mapper/ | 规则 Mapper |
| KbIntentKbRelMapper | mapper/ | 关联 Mapper |
| IntentService | service/ | 核心业务逻辑（CRUD + 匹配算法） |
| IntentController | web/ | REST API |

所有在 collaboration-service 模块（8082）。

## 错误处理

- 相关节点不存在 → PARAM_INVALID
- 删除场景时级联删除所有子意图+规则+关联
- 规则表达式冲突 → 前端提示，后端不做唯一限制
- 知识库已删除但关联仍在 → 关联表保留 kbId，前端展示时标记"已删除"
