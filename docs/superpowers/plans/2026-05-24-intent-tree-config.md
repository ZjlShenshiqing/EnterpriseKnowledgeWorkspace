# 意图树配置系统 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为管理后台的意图树配置和意图列表提供完整后端 API + 前端树形编辑器 + 匹配规则管理 + 知识库绑定 + 实时匹配预览。

**Architecture:** collaboration-service（8082）新增 3 张表 + 3 个 Entity + 3 个 Mapper + IntentService + IntentController（13 个端点）。前端 IntentConfig.vue 重写为左侧树 + 右侧详情面板布局，IntentList.vue 改为从 API 获取数据。

**Tech Stack:** Spring Boot 3.4.4, MyBatis-Plus 3.5.7, Vue 3 + Element Plus

---

### Task 1: 数据库 — 新增三张表

**Files:**
- Modify: `resouces/enterprise_knowledge_workspace.sql`

- [ ] **Step 1: 在集中式 SQL 中新增 DROP 和 CREATE TABLE**

在 `kb_agent_message` 建表语句之后新增：

```sql
-- -------------------- kb_intent_node 意图节点表 --------------------
DROP TABLE IF EXISTS kb_intent_node;
CREATE TABLE kb_intent_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parent_id BIGINT NULL COMMENT '父节点ID，NULL为根场景',
    name VARCHAR(128) NOT NULL COMMENT '节点名称',
    level TINYINT NOT NULL DEFAULT 1 COMMENT '层级 1=场景 2=意图',
    sort_order INT DEFAULT 0 COMMENT '同级排序',
    description VARCHAR(512) NULL COMMENT '节点说明',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_intent_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图节点表';

-- -------------------- kb_intent_rule 意图匹配规则表 --------------------
DROP TABLE IF EXISTS kb_intent_rule;
CREATE TABLE kb_intent_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '意图节点ID',
    rule_type VARCHAR(16) NOT NULL COMMENT 'keyword / regex',
    expression VARCHAR(256) NOT NULL COMMENT '关键词或正则表达式',
    weight DOUBLE NOT NULL DEFAULT 1.0 COMMENT '匹配权重',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rule_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图匹配规则表';

-- -------------------- kb_intent_kb_rel 意图知识库关联表 --------------------
DROP TABLE IF EXISTS kb_intent_kb_rel;
CREATE TABLE kb_intent_kb_rel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '意图节点ID',
    kb_id BIGINT NOT NULL COMMENT '知识库ID',
    weight DOUBLE NOT NULL DEFAULT 1.0 COMMENT '检索权重',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intent_kb (node_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图知识库关联表';
```

在所有 DROP TABLE 区域（文件末尾的 sys_doc DROP 之前）也加上对应的 DROP：

```sql
DROP TABLE IF EXISTS kb_intent_kb_rel;
DROP TABLE IF EXISTS kb_intent_rule;
DROP TABLE IF EXISTS kb_intent_node;
```

- [ ] **Step 2: 创建迁移脚本**

Create: `enterprise-collaboration-service/src/main/resources/db/migration/004-intent-tree.sql`

```sql
CREATE TABLE IF NOT EXISTS kb_intent_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parent_id BIGINT NULL COMMENT '父节点ID，NULL为根场景',
    name VARCHAR(128) NOT NULL COMMENT '节点名称',
    level TINYINT NOT NULL DEFAULT 1 COMMENT '层级 1=场景 2=意图',
    sort_order INT DEFAULT 0 COMMENT '同级排序',
    description VARCHAR(512) NULL COMMENT '节点说明',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_intent_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图节点表';

CREATE TABLE IF NOT EXISTS kb_intent_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '意图节点ID',
    rule_type VARCHAR(16) NOT NULL COMMENT 'keyword / regex',
    expression VARCHAR(256) NOT NULL COMMENT '关键词或正则表达式',
    weight DOUBLE NOT NULL DEFAULT 1.0 COMMENT '匹配权重',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rule_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图匹配规则表';

CREATE TABLE IF NOT EXISTS kb_intent_kb_rel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    node_id BIGINT NOT NULL COMMENT '意图节点ID',
    kb_id BIGINT NOT NULL COMMENT '知识库ID',
    weight DOUBLE NOT NULL DEFAULT 1.0 COMMENT '检索权重',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intent_kb (node_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图知识库关联表';
```

- [ ] **Step 3: Commit**

```bash
git add resouces/enterprise_knowledge_workspace.sql enterprise-collaboration-service/src/main/resources/db/migration/004-intent-tree.sql
git commit -m "feat: 新增意图树三张表 — kb_intent_node/rule/kb_rel

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: Entity 层 — 三个实体类

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbIntentNode.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbIntentRule.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbIntentKbRel.java`

- [ ] **Step 1: 创建 KbIntentNode.java**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_intent_node")
public class KbIntentNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private java.util.List<KbIntentNode> children;
    @TableField(exist = false)
    private java.util.List<KbIntentRule> rules;
    @TableField(exist = false)
    private java.util.List<KbIntentKbRel> kbRels;
}
```

- [ ] **Step 2: 创建 KbIntentRule.java**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_intent_rule")
public class KbIntentRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private String ruleType;
    private String expression;
    private Double weight;
    private Integer enabled;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 KbIntentKbRel.java**

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_intent_kb_rel")
public class KbIntentKbRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long kbId;
    private Double weight;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbIntentNode.java enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbIntentRule.java enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbIntentKbRel.java
git commit -m "feat: 新增意图树三个实体类 KbIntentNode/KbIntentRule/KbIntentKbRel

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Mapper 层 — 三个 Mapper 接口

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbIntentNodeMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbIntentRuleMapper.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbIntentKbRelMapper.java`

- [ ] **Step 1: 创建三个 Mapper**

KbIntentNodeMapper.java:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.KbIntentNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbIntentNodeMapper extends BaseMapper<KbIntentNode> {
}
```

KbIntentRuleMapper.java:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.KbIntentRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbIntentRuleMapper extends BaseMapper<KbIntentRule> {
}
```

KbIntentKbRelMapper.java:
```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.KbIntentKbRel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbIntentKbRelMapper extends BaseMapper<KbIntentKbRel> {
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbIntentNodeMapper.java enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbIntentRuleMapper.java enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbIntentKbRelMapper.java
git commit -m "feat: 新增意图树三个 Mapper 接口

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: IntentService — 核心业务逻辑（CRUD + 匹配）

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/IntentService.java`

- [ ] **Step 1: 创建 IntentService.java**

```java
package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private final KbIntentNodeMapper nodeMapper;
    private final KbIntentRuleMapper ruleMapper;
    private final KbIntentKbRelMapper kbRelMapper;

    /** 获取全量意图树 */
    public List<KbIntentNode> getTree() {
        List<KbIntentNode> all = nodeMapper.selectList(
                Wrappers.lambdaQuery(KbIntentNode.class).orderByAsc(KbIntentNode::getSortOrder));
        Map<Long, List<KbIntentNode>> childrenMap = all.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(KbIntentNode::getParentId));
        List<KbIntentNode> roots = all.stream()
                .filter(n -> n.getParentId() == null)
                .collect(Collectors.toList());
        for (KbIntentNode root : roots) {
            buildChildren(root, childrenMap);
        }
        return roots;
    }

    private void buildChildren(KbIntentNode parent, Map<Long, List<KbIntentNode>> childrenMap) {
        List<KbIntentNode> children = childrenMap.getOrDefault(parent.getId(), List.of());
        parent.setChildren(children);
        for (KbIntentNode child : children) {
            buildChildren(child, childrenMap);
        }
    }

    /** 获取节点详情（含规则和知识库关联） */
    public KbIntentNode getNode(Long id) {
        KbIntentNode node = nodeMapper.selectById(id);
        if (node != null) {
            node.setRules(ruleMapper.selectList(
                    Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getNodeId, id)));
            node.setKbRels(kbRelMapper.selectList(
                    Wrappers.lambdaQuery(KbIntentKbRel.class).eq(KbIntentKbRel::getNodeId, id)));
        }
        return node;
    }

    /** 新增节点 */
    public KbIntentNode createNode(KbIntentNode node) {
        if (node.getSortOrder() == null) node.setSortOrder(0);
        if (node.getLevel() == null) node.setLevel(1);
        if (node.getEnabled() == null) node.setEnabled(1);
        nodeMapper.insert(node);
        return node;
    }

    /** 更新节点 */
    public void updateNode(Long id, KbIntentNode node) {
        node.setId(id);
        nodeMapper.updateById(node);
    }

    /** 删除节点（级联删除子节点+规则+关联） */
    public void deleteNode(Long id) {
        List<KbIntentNode> children = nodeMapper.selectList(
                Wrappers.lambdaQuery(KbIntentNode.class).eq(KbIntentNode::getParentId, id));
        for (KbIntentNode child : children) {
            deleteNode(child.getId());
        }
        ruleMapper.delete(Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getNodeId, id));
        kbRelMapper.delete(Wrappers.lambdaQuery(KbIntentKbRel.class).eq(KbIntentKbRel::getNodeId, id));
        nodeMapper.deleteById(id);
    }

    /** 排序 */
    public void updateSort(Long id, Long parentId, Integer sortOrder) {
        KbIntentNode node = new KbIntentNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setSortOrder(sortOrder);
        nodeMapper.updateById(node);
    }

    /** 获取节点规则列表 */
    public List<KbIntentRule> getRules(Long nodeId) {
        return ruleMapper.selectList(
                Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getNodeId, nodeId));
    }

    /** 新增规则 */
    public KbIntentRule createRule(Long nodeId, KbIntentRule rule) {
        rule.setNodeId(nodeId);
        if (rule.getWeight() == null) rule.setWeight(1.0);
        if (rule.getEnabled() == null) rule.setEnabled(1);
        ruleMapper.insert(rule);
        return rule;
    }

    /** 更新规则 */
    public void updateRule(Long ruleId, KbIntentRule rule) {
        rule.setId(ruleId);
        ruleMapper.updateById(rule);
    }

    /** 删除规则 */
    public void deleteRule(Long ruleId) {
        ruleMapper.deleteById(ruleId);
    }

    /** 获取节点关联知识库 */
    public List<KbIntentKbRel> getKbRels(Long nodeId) {
        return kbRelMapper.selectList(
                Wrappers.lambdaQuery(KbIntentKbRel.class).eq(KbIntentKbRel::getNodeId, nodeId));
    }

    /** 绑定知识库 */
    public KbIntentKbRel bindKb(Long nodeId, Long kbId, Double weight) {
        KbIntentKbRel rel = new KbIntentKbRel();
        rel.setNodeId(nodeId);
        rel.setKbId(kbId);
        rel.setWeight(weight != null ? weight : 1.0);
        kbRelMapper.insert(rel);
        return rel;
    }

    /** 更新关联权重 */
    public void updateKbRel(Long relId, Double weight) {
        KbIntentKbRel rel = new KbIntentKbRel();
        rel.setId(relId);
        rel.setWeight(weight);
        kbRelMapper.updateById(rel);
    }

    /** 解除绑定 */
    public void unbindKb(Long relId) {
        kbRelMapper.deleteById(relId);
    }

    /** 匹配测试 */
    public Map<String, Object> match(String query) {
        List<KbIntentNode> allNodes = nodeMapper.selectList(
                Wrappers.lambdaQuery(KbIntentNode.class).eq(KbIntentNode::getEnabled, 1));
        List<KbIntentRule> allRules = ruleMapper.selectList(
                Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getEnabled, 1));

        Map<Long, List<KbIntentRule>> rulesByNode = allRules.stream()
                .collect(Collectors.groupingBy(KbIntentRule::getNodeId));

        Map<Long, KbIntentNode> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(KbIntentNode::getId, n -> n));

        List<Map<String, Object>> hits = new ArrayList<>();

        for (KbIntentRule rule : allRules) {
            boolean matched = false;
            if ("keyword".equals(rule.getRuleType())) {
                matched = query.contains(rule.getExpression());
            } else if ("regex".equals(rule.getRuleType())) {
                try {
                    matched = Pattern.compile(rule.getExpression()).matcher(query).find();
                } catch (Exception ignored) {}
            }
            if (matched) {
                KbIntentNode node = nodeMap.get(rule.getNodeId());
                if (node != null) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("nodeId", node.getId());
                    hit.put("nodeName", node.getName());
                    hit.put("ruleId", rule.getId());
                    hit.put("expression", rule.getExpression());
                    hit.put("ruleType", rule.getRuleType());
                    hit.put("weight", rule.getWeight());
                    hits.add(hit);
                }
            }
        }

        hits.sort((a, b) -> Double.compare(
                (Double) b.get("weight"), (Double) a.get("weight")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("hits", hits);
        if (!hits.isEmpty()) {
            result.put("bestMatch", hits.get(0));
        }
        return result;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/IntentService.java
git commit -m "feat: 新增 IntentService — 意图树 CRUD + 匹配算法

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: IntentController — 13 个 REST 端点

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/IntentController.java`

- [ ] **Step 1: 创建 IntentController.java**

```java
package com.zjl.collaboration.web;

import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.service.IntentService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/intents")
@RequiredArgsConstructor
public class IntentController {

    private final IntentService intentService;

    // ---- 节点管理 ----

    @GetMapping("/nodes")
    public Result<List<KbIntentNode>> getTree() {
        return Results.success(intentService.getTree());
    }

    @GetMapping("/nodes/{id}")
    public Result<KbIntentNode> getNode(@PathVariable Long id) {
        return Results.success(intentService.getNode(id));
    }

    @PostMapping("/nodes")
    public Result<KbIntentNode> createNode(@RequestBody KbIntentNode node) {
        return Results.success(intentService.createNode(node));
    }

    @PutMapping("/nodes/{id}")
    public Result<Void> updateNode(@PathVariable Long id, @RequestBody KbIntentNode node) {
        intentService.updateNode(id, node);
        return Results.success();
    }

    @DeleteMapping("/nodes/{id}")
    public Result<Void> deleteNode(@PathVariable Long id) {
        intentService.deleteNode(id);
        return Results.success();
    }

    @PutMapping("/nodes/{id}/sort")
    public Result<Void> updateSort(@PathVariable Long id, @RequestBody SortReq req) {
        intentService.updateSort(id, req.getParentId(), req.getSortOrder());
        return Results.success();
    }

    // ---- 规则管理 ----

    @GetMapping("/nodes/{id}/rules")
    public Result<List<KbIntentRule>> getRules(@PathVariable Long id) {
        return Results.success(intentService.getRules(id));
    }

    @PostMapping("/nodes/{id}/rules")
    public Result<KbIntentRule> createRule(@PathVariable Long id, @RequestBody KbIntentRule rule) {
        return Results.success(intentService.createRule(id, rule));
    }

    @PutMapping("/rules/{ruleId}")
    public Result<Void> updateRule(@PathVariable Long ruleId, @RequestBody KbIntentRule rule) {
        intentService.updateRule(ruleId, rule);
        return Results.success();
    }

    @DeleteMapping("/rules/{ruleId}")
    public Result<Void> deleteRule(@PathVariable Long ruleId) {
        intentService.deleteRule(ruleId);
        return Results.success();
    }

    // ---- 知识库关联 ----

    @GetMapping("/nodes/{id}/kbs")
    public Result<List<KbIntentKbRel>> getKbRels(@PathVariable Long id) {
        return Results.success(intentService.getKbRels(id));
    }

    @PostMapping("/nodes/{id}/kbs")
    public Result<KbIntentKbRel> bindKb(@PathVariable Long id, @RequestBody BindKbReq req) {
        return Results.success(intentService.bindKb(id, req.getKbId(), req.getWeight()));
    }

    @PutMapping("/kb-rel/{relId}")
    public Result<Void> updateKbRel(@PathVariable Long relId, @RequestBody BindKbReq req) {
        intentService.updateKbRel(relId, req.getWeight());
        return Results.success();
    }

    @DeleteMapping("/kb-rel/{relId}")
    public Result<Void> unbindKb(@PathVariable Long relId) {
        intentService.unbindKb(relId);
        return Results.success();
    }

    // ---- 匹配预览 ----

    @PostMapping("/match")
    public Result<Map<String, Object>> match(@RequestBody MatchReq req) {
        return Results.success(intentService.match(req.getQuery()));
    }

    @Data public static class SortReq { private Long parentId; private Integer sortOrder; }
    @Data public static class BindKbReq { private Long kbId; private Double weight; }
    @Data public static class MatchReq { private String query; }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/IntentController.java
git commit -m "feat: 新增 IntentController — 意图树 13 个 REST 端点

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: 前端 — 重写 IntentConfig.vue（树 + 详情面板）

**Files:**
- Modify: `enterprise-web/src/pages/admin/IntentConfig.vue`

- [ ] **Step 1: 编写完整的 IntentConfig.vue**

```vue
<template>
  <div class="admin-view" style="display:flex;gap:0;height:calc(100vh - 120px);padding:0">
    <!-- 左侧树 -->
    <div style="width:280px;border-right:1px solid #e5e6eb;display:flex;flex-direction:column;background:#fafafa;flex-shrink:0">
      <div style="padding:14px 16px;border-bottom:1px solid #e5e6eb">
        <el-button type="primary" size="small" @click="addRoot">新增场景</el-button>
      </div>
      <div style="flex:1;overflow-y:auto;padding:8px 0">
        <div v-for="node in tree" :key="node.id">
          <div @click="selectNode(node)"
            :style="{padding:'8px 16px',cursor:'pointer',display:'flex',alignItems:'center',gap:'6px',background:selectedNode?.id===node.id?'#e8f3ff':'transparent',color:selectedNode?.id===node.id?'#3370ff':'#1f2329',fontWeight:node.level===1?600:400}">
            <span style="font-size:12px">{{ node.level===1 ? '▸' : '├' }}</span>
            <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px">{{ node.name }}</span>
            <span v-if="!node.enabled" style="color:#bbb;font-size:10px">停用</span>
          </div>
          <div v-if="node.children && node.children.length && expandedIds.includes(node.id)" style="padding-left:16px">
            <div v-for="child in node.children" :key="child.id"
              @click="selectNode(child)"
              :style="{padding:'6px 16px',cursor:'pointer',display:'flex',alignItems:'center',gap:'6px',background:selectedNode?.id===child.id?'#e8f3ff':'transparent',color:selectedNode?.id===child.id?'#3370ff':'#555'}">
              <span style="font-size:12px">├</span>
              <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px">{{ child.name }}</span>
            </div>
          </div>
        </div>
        <div v-if="tree.length===0" style="text-align:center;padding:30px;color:#bbb;font-size:13px">暂无意图节点</div>
      </div>
    </div>

    <!-- 右侧详情 -->
    <div style="flex:1;overflow-y:auto;padding:20px;background:#fff">
      <div v-if="!selectedNode" style="display:flex;align-items:center;justify-content:center;height:100%;color:#bbb;font-size:14px">
        选择一个节点查看详情
      </div>
      <template v-else>
        <!-- 节点信息 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px;margin-bottom:16px">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">节点信息</div>
          <div style="display:flex;gap:12px;align-items:flex-end;flex-wrap:wrap">
            <div>
              <div style="font-size:11px;color:#8f959e;margin-bottom:4px">名称</div>
              <input v-model="form.name" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:180px;outline:none" />
            </div>
            <div>
              <div style="font-size:11px;color:#8f959e;margin-bottom:4px">层级</div>
              <select v-model="form.level" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;outline:none">
                <option :value="1">场景</option>
                <option :value="2">意图</option>
              </select>
            </div>
            <div>
              <div style="font-size:11px;color:#8f959e;margin-bottom:4px">排序</div>
              <input v-model.number="form.sortOrder" type="number" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:70px;outline:none" />
            </div>
            <label style="display:flex;align-items:center;gap:4px;font-size:12px;cursor:pointer">
              <input type="checkbox" v-model="form.enabled" :true-value="1" :false-value="0" /> 启用
            </label>
            <el-button type="primary" size="small" @click="saveNode">保存</el-button>
            <el-button size="small" type="danger" @click="deleteSelected">删除</el-button>
          </div>
          <div style="margin-top:10px">
            <div style="font-size:11px;color:#8f959e;margin-bottom:4px">描述</div>
            <input v-model="form.description" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100%;outline:none" placeholder="可选描述" />
          </div>
        </div>

        <!-- 匹配规则 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px;margin-bottom:16px">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
            <span style="font-weight:600;font-size:14px">匹配规则</span>
            <el-button type="primary" size="small" @click="addRule">新增规则</el-button>
          </div>
          <table v-if="rules.length" style="width:100%;border-collapse:collapse;font-size:12px">
            <tr style="border-bottom:1px solid #e5e6eb;color:#8f959e;text-align:left">
              <th style="padding:6px 8px;font-weight:500">类型</th>
              <th style="padding:6px 8px;font-weight:500">表达式</th>
              <th style="padding:6px 8px;font-weight:500;width:60px">权重</th>
              <th style="padding:6px 8px;font-weight:500;width:40px">启用</th>
              <th style="padding:6px 8px;font-weight:500;width:40px"></th>
            </tr>
            <tr v-for="r in rules" :key="r.id" style="border-bottom:1px solid #f2f3f5">
              <td style="padding:6px 8px">{{ r.ruleType }}</td>
              <td style="padding:6px 8px;font-family:monospace">{{ r.expression }}</td>
              <td style="padding:6px 8px">{{ r.weight }}</td>
              <td style="padding:6px 8px">{{ r.enabled ? '✓' : '✗' }}</td>
              <td style="padding:6px 8px"><span @click="deleteRule(r.id)" style="color:#f54a45;cursor:pointer;font-size:14px">×</span></td>
            </tr>
          </table>
          <div v-else style="color:#bbb;font-size:12px;text-align:center;padding:16px">暂无规则</div>
        </div>

        <!-- 关联知识库 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px;margin-bottom:16px">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
            <span style="font-weight:600;font-size:14px">关联知识库</span>
            <div style="display:flex;gap:6px">
              <select v-model="newKbId" style="border:1px solid #e5e6eb;border-radius:6px;padding:5px 8px;font-size:12px;outline:none">
                <option :value="null">选择知识库</option>
                <option v-for="kb in kbList" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
              </select>
              <input v-model.number="newKbWeight" type="number" step="0.1" placeholder="权重" style="border:1px solid #e5e6eb;border-radius:6px;padding:5px 8px;font-size:12px;width:60px;outline:none" />
              <el-button size="small" @click="bindKb">绑定</el-button>
            </div>
          </div>
          <table v-if="kbRels.length" style="width:100%;border-collapse:collapse;font-size:12px">
            <tr style="border-bottom:1px solid #e5e6eb;color:#8f959e;text-align:left">
              <th style="padding:6px 8px;font-weight:500">知识库</th>
              <th style="padding:6px 8px;font-weight:500;width:60px">权重</th>
              <th style="padding:6px 8px;font-weight:500;width:40px"></th>
            </tr>
            <tr v-for="r in kbRels" :key="r.id" style="border-bottom:1px solid #f2f3f5">
              <td style="padding:6px 8px">{{ getKbName(r.kbId) }}</td>
              <td style="padding:6px 8px">{{ r.weight }}</td>
              <td style="padding:6px 8px"><span @click="unbindKb(r.id)" style="color:#f54a45;cursor:pointer;font-size:14px">×</span></td>
            </tr>
          </table>
          <div v-else style="color:#bbb;font-size:12px;text-align:center;padding:16px">暂未关联知识库</div>
        </div>

        <!-- 匹配预览 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">匹配预览</div>
          <div style="display:flex;gap:8px;margin-bottom:12px">
            <input v-model="testQuery" @keydown.enter="testMatch" placeholder="输入测试文本，如'请假流程怎么走'" style="flex:1;border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;outline:none" />
            <el-button type="primary" size="small" @click="testMatch">测试匹配</el-button>
          </div>
          <div v-if="matchResult">
            <div style="font-size:12px;font-weight:500;margin-bottom:4px" v-if="matchResult.bestMatch">
              最佳命中: <span style="color:#3370ff">{{ matchResult.bestMatch.nodeName }}</span>
              ({{ matchResult.bestMatch.ruleType }}: {{ matchResult.bestMatch.expression }})
            </div>
            <div v-if="matchResult.hits && matchResult.hits.length===0" style="color:#bbb;font-size:12px">无命中</div>
            <div v-for="h in matchResult.hits" :key="h.ruleId" style="font-size:11px;color:#555;padding:2px 0">
              {{ h.nodeName }} ← {{ h.ruleType }}:"{{ h.expression }}" (权重 {{ h.weight }})
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="ruleDialogVisible" title="编辑匹配规则" width="420px">
      <div style="display:flex;flex-direction:column;gap:12px">
        <div>
          <div style="font-size:12px;color:#8f959e;margin-bottom:4px">规则类型</div>
          <select v-model="ruleForm.ruleType" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100%;outline:none">
            <option value="keyword">关键词</option>
            <option value="regex">正则表达式</option>
          </select>
        </div>
        <div>
          <div style="font-size:12px;color:#8f959e;margin-bottom:4px">表达式</div>
          <input v-model="ruleForm.expression" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100%;outline:none" placeholder="关键词或正则表达式" />
        </div>
        <div>
          <div style="font-size:12px;color:#8f959e;margin-bottom:4px">权重</div>
          <input v-model.number="ruleForm.weight" type="number" step="0.1" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100px;outline:none" />
        </div>
        <label style="font-size:12px;display:flex;align-items:center;gap:4px;cursor:pointer">
          <input type="checkbox" v-model="ruleForm.enabled" :true-value="1" :false-value="0" /> 启用
        </label>
      </div>
      <template #footer>
        <el-button @click="ruleDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthHeaders, forceLogout } from '../../api'

const tree = ref([])
const selectedNode = ref(null)
const rules = ref([])
const kbRels = ref([])
const kbList = ref([])
const expandedIds = ref([])
const matchResult = ref(null)

const form = ref({})
const ruleForm = ref({ ruleType: 'keyword', expression: '', weight: 1.0, enabled: 1 })
const ruleDialogVisible = ref(false)
const editingRuleId = ref(null)
const testQuery = ref('')
const newKbId = ref(null)
const newKbWeight = ref(1.0)

function auth() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

async function loadTree() {
  const r = await fetch('/api/intents/nodes', { headers: auth() })
  const body = await r.json()
  if (body.code === 40100) { forceLogout(); return }
  tree.value = body.data || []
}

async function loadKbList() {
  const r = await fetch('/api/kb/bases', { headers: auth() })
  const body = await r.json()
  if (body.code === 200) kbList.value = body.data || []
}

async function selectNode(node) {
  selectedNode.value = node
  form.value = { name: node.name, level: node.level, sortOrder: node.sortOrder, description: node.description || '', enabled: node.enabled }
  if (expandedIds.value.includes(node.id)) {
    expandedIds.value = expandedIds.value.filter(id => id !== node.id)
  } else {
    expandedIds.value.push(node.id)
  }
  const r = await fetch(`/api/intents/nodes/${node.id}`, { headers: auth() })
  const body = await r.json()
  if (body.code === 200) {
    rules.value = body.data.rules || []
    kbRels.value = body.data.kbRels || []
  }
}

async function saveNode() {
  await fetch(`/api/intents/nodes/${selectedNode.value.id}`, {
    method: 'PUT', headers: auth(), body: JSON.stringify(form.value)
  })
  ElMessage.success('已保存')
  loadTree()
}

async function deleteSelected() {
  await ElMessageBox.confirm('删除该节点将级联删除所有子节点、规则和关联，确定？', '确认删除', { type: 'warning' })
  await fetch(`/api/intents/nodes/${selectedNode.value.id}`, { method: 'DELETE', headers: auth() })
  selectedNode.value = null
  ElMessage.success('已删除')
  loadTree()
}

async function addRoot() {
  const name = prompt('场景名称:')
  if (!name) return
  await fetch('/api/intents/nodes', {
    method: 'POST', headers: auth(), body: JSON.stringify({ name, level: 1, sortOrder: 0, enabled: 1 })
  })
  loadTree()
}

async function addRule() {
  editingRuleId.value = null
  ruleForm.value = { ruleType: 'keyword', expression: '', weight: 1.0, enabled: 1 }
  ruleDialogVisible.value = true
}

async function saveRule() {
  const nodeId = selectedNode.value.id
  if (editingRuleId.value) {
    await fetch(`/api/intents/rules/${editingRuleId.value}`, {
      method: 'PUT', headers: auth(), body: JSON.stringify(ruleForm.value)
    })
  } else {
    await fetch(`/api/intents/nodes/${nodeId}/rules`, {
      method: 'POST', headers: auth(), body: JSON.stringify(ruleForm.value)
    })
  }
  ruleDialogVisible.value = false
  selectNode(selectedNode.value)
}

async function deleteRule(ruleId) {
  await fetch(`/api/intents/rules/${ruleId}`, { method: 'DELETE', headers: auth() })
  selectNode(selectedNode.value)
}

async function bindKb() {
  if (!newKbId.value) return
  await fetch(`/api/intents/nodes/${selectedNode.value.id}/kbs`, {
    method: 'POST', headers: auth(), body: JSON.stringify({ kbId: Number(newKbId.value), weight: newKbWeight.value || 1.0 })
  })
  newKbId.value = null
  newKbWeight.value = 1.0
  ElMessage.success('已绑定')
  selectNode(selectedNode.value)
}

async function unbindKb(relId) {
  await fetch(`/api/intents/kb-rel/${relId}`, { method: 'DELETE', headers: auth() })
  selectNode(selectedNode.value)
}

async function testMatch() {
  if (!testQuery.value.trim()) return
  const r = await fetch('/api/intents/match', {
    method: 'POST', headers: auth(), body: JSON.stringify({ query: testQuery.value })
  })
  matchResult.value = (await r.json()).data
}

function getKbName(kbId) {
  const kb = kbList.value.find(k => k.id === kbId)
  return kb ? kb.name : `知识库#${kbId}`
}

onMounted(() => { loadTree(); loadKbList() })
</script>
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-web/src/pages/admin/IntentConfig.vue
git commit -m "feat: 重写 IntentConfig 意图树配置页 — 左侧树 + 右侧详情 + 匹配预览

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: 前端 — 更新 IntentList.vue（真实数据）

**Files:**
- Modify: `enterprise-web/src/pages/admin/IntentList.vue`

- [ ] **Step 1: 重写 IntentList.vue**

```vue
<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">意图列表</div>
        <div class="admin-page-subtitle">查看每个意图的规则、关联知识库和状态。</div>
      </div>
    </section>
    <section class="admin-table-card">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="name" label="意图名称" min-width="150" />
        <el-table-column prop="parentName" label="所属场景" min-width="120" />
        <el-table-column label="规则数" width="80">
          <template #default="{ row }">{{ row.ruleCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="关联知识库" min-width="200">
          <template #default="{ row }">{{ row.kbNames || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span :style="{color:row.enabled?'#34c759':'#8f959e'}">{{ row.enabled ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAuthHeaders, forceLogout } from '../../api'

const rows = ref([])
const loading = ref(false)

function auth() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

onMounted(async () => {
  loading.value = true
  const r = await fetch('/api/intents/nodes', { headers: auth() })
  const body = await r.json()
  if (body.code === 40100) { forceLogout(); return }
  const tree = body.data || []
  const flat = []
  function walk(nodes, parentName) {
    for (const n of nodes) {
      flat.push({
        id: n.id,
        name: n.name,
        parentName: parentName || '-',
        level: n.level,
        enabled: n.enabled,
        ruleCount: n.rules ? n.rules.length : 0,
        kbNames: n.kbRels ? n.kbRels.map(r => `KB#${r.kbId}`).join(', ') : '-',
        updatedAt: n.updatedAt
      })
      if (n.children) walk(n.children, n.name)
    }
  }
  walk(tree, null)
  rows.value = flat
  loading.value = false
})
</script>
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-web/src/pages/admin/IntentList.vue
git commit -m "feat: IntentList 改为从 API 获取真实意图数据

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 8: 端到端验证

- [ ] **Step 1: 编译后端**

```bash
mvn clean package -pl enterprise-collaboration-service -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 启动协作服务**

```bash
mvn spring-boot:run -pl enterprise-collaboration-service
```

Expected: 服务启动成功，无报错

- [ ] **Step 3: 执行建表 SQL**

连接到 `enterprise_collaboration` 数据库，执行迁移脚本 `004-intent-tree.sql`

- [ ] **Step 4: 验证 API**

```bash
# 创建场景
curl -X POST http://localhost:8090/api/intents/nodes \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"name":"知识问答","level":1}'

# 创建子意图
curl -X POST http://localhost:8090/api/intents/nodes \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"name":"制度查询","level":2,"parentId":1}'

# 添加规则
curl -X POST http://localhost:8090/api/intents/nodes/2/rules \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"ruleType":"keyword","expression":"报销","weight":1.5}'

# 测试匹配
curl -X POST http://localhost:8090/api/intents/match \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"query":"报销流程怎么走"}'

# 获取树
curl http://localhost:8090/api/intents/nodes -H "X-User-Id: 1"
```

- [ ] **Step 5: 验证前端**

打开浏览器 → 管理后台 → 意图树配置
- 创建场景 → 创建子意图
- 添加规则 → 测试匹配
- 绑定知识库
- 查看意图列表页

- [ ] **Step 6: Commit（如有修复）**

```bash
git add -A
git commit -m "chore: 端到端验证修复

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```
