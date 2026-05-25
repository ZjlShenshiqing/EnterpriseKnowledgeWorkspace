# 关键词映射系统 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建独立的关键词→知识库映射系统，替代 KeywordMappings.vue 中的 mock 数据

**Architecture:** 协作服务新增 Flyway 迁移 + 实体 + Mapper + Controller；前端去掉 mock 调用真实 API；网关添加路由

**Tech Stack:** MyBatis-Plus, Flyway, Vue 3 + Element Plus, Spring Cloud Gateway

---

## File Structure

```
enterprise-collaboration-service/
  src/main/resources/db/migration/
    V007__keyword_mapping.sql                       ← 新建（建表）
  src/main/java/com/zjl/collaboration/
    entity/KbKeywordMapping.java                    ← 新建（实体）
    mapper/KbKeywordMappingMapper.java              ← 新建（Mapper）
    web/KeywordMappingController.java               ← 新建（REST 控制器）

enterprise-gateway-service/
  src/main/resources/application.yml                ← 修改（路由）

enterprise-web/
  src/pages/admin/KeywordMappings.vue               ← 修改（去 mock，接 API）
```

---

### Task 1: Flyway 迁移脚本 + 实体 + Mapper

**Files:**
- Create: `enterprise-collaboration-service/src/main/resources/db/migration/V007__keyword_mapping.sql`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbKeywordMapping.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbKeywordMappingMapper.java`

- [ ] **Step 1: 创建 Flyway 迁移脚本**

`V007__keyword_mapping.sql`：

```sql
CREATE TABLE IF NOT EXISTS kb_keyword_mapping (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    keyword     VARCHAR(100) NOT NULL,
    kb_name     VARCHAR(100) NOT NULL,
    priority    INT          NOT NULL DEFAULT 0,
    strategy    VARCHAR(255) DEFAULT NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 创建实体类**

`KbKeywordMapping.java`：

```java
package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_keyword_mapping")
public class KbKeywordMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private String kbName;
    private Integer priority;
    private String strategy;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 Mapper 接口**

`KbKeywordMappingMapper.java`：

```java
package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.KbKeywordMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbKeywordMappingMapper extends BaseMapper<KbKeywordMapping> {
}
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-collaboration-service/src/main/resources/db/migration/V007__keyword_mapping.sql \
        enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/KbKeywordMapping.java \
        enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/KbKeywordMappingMapper.java
git commit -m "feat: 关键词映射 — Flyway 迁移 + 实体 + Mapper

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: KeywordMappingController — CRUD + Match API

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/KeywordMappingController.java`

- [ ] **Step 1: 创建控制器**

```java
package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.KbKeywordMapping;
import com.zjl.collaboration.mapper.KbKeywordMappingMapper;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/keyword-mappings")
@RequiredArgsConstructor
public class KeywordMappingController {

    private final KbKeywordMappingMapper mapper;

    @GetMapping
    public Result<PageResult<KbKeywordMapping>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        var wrapper = new LambdaQueryWrapper<KbKeywordMapping>()
                .orderByDesc(KbKeywordMapping::getPriority);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(KbKeywordMapping::getKeyword, keyword);
        }
        Page<KbKeywordMapping> page = mapper.selectPage(new Page<>(current, size), wrapper);
        return Results.success(PageResult.of(current, size, page.getTotal(), page.getRecords()));
    }

    @PostMapping
    public Result<KbKeywordMapping> create(@RequestBody KbKeywordMapping mapping) {
        mapping.setEnabled(mapping.getEnabled() != null ? mapping.getEnabled() : 1);
        mapper.insert(mapping);
        return Results.success(mapping);
    }

    @PutMapping("/{id}")
    public Result<KbKeywordMapping> update(@PathVariable Long id, @RequestBody KbKeywordMapping mapping) {
        mapping.setId(id);
        mapper.updateById(mapping);
        return Results.success(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Results.success();
    }

    @PostMapping("/match")
    public Result<Map<String, Object>> match(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        if (query.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("hits", List.of());
            return Results.success(result);
        }
        List<KbKeywordMapping> all = mapper.selectList(
                new LambdaQueryWrapper<KbKeywordMapping>()
                        .eq(KbKeywordMapping::getEnabled, 1)
                        .orderByDesc(KbKeywordMapping::getPriority));
        List<KbKeywordMapping> hits = all.stream()
                .filter(m -> query.contains(m.getKeyword()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("hits", hits);
        return Results.success(result);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/KeywordMappingController.java
git commit -m "feat: KeywordMappingController — 关键词映射 CRUD + 匹配

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: 网关路由配置

**Files:**
- Modify: `enterprise-gateway-service/src/main/resources/application.yml`

- [ ] **Step 1: 在 collaboration 路由中添加 /api/keyword-mappings/****

修改 gateway application.yml，在 collaboration 路由的 predicates 的 Path 值末尾加上 `/api/keyword-mappings/**`。

当前第 27 行：
```yaml
            - Path=/api/meetings/**,/api/todos/**,/api/tasks/**,/api/notifications/**,/api/chat/**,/api/docs/**,/api/approvals/**,/api/announcements/**,/api/intents/**
```

改为：
```yaml
            - Path=/api/meetings/**,/api/todos/**,/api/tasks/**,/api/notifications/**,/api/chat/**,/api/docs/**,/api/approvals/**,/api/announcements/**,/api/intents/**,/api/keyword-mappings/**
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-gateway-service/src/main/resources/application.yml
git commit -m "feat: 网关添加 /api/keyword-mappings/** 路由到协作服务

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: 前端 KeywordMappings.vue — 去 mock 接 API

**Files:**
- Modify: `enterprise-web/src/pages/admin/KeywordMappings.vue`

- [ ] **Step 1: 替换整个文件内容**

```vue
<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">关键词映射</div>
        <div class="admin-page-subtitle">管理高频关键词到知识库和应答策略的映射关系。</div>
      </div>
      <div class="admin-actions">
        <el-button type="primary" @click="openCreate">新增映射</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">映射规则</div>
          <div class="admin-toolbar-subtitle">优先级越高，越先命中。</div>
        </div>
        <el-input v-model="searchKeyword" placeholder="搜索关键词" clearable style="width:200px" @input="loadData" />
      </div>
      <el-table :data="mappings" v-loading="loading">
        <el-table-column prop="keyword" label="关键词" min-width="120" />
        <el-table-column prop="kbName" label="目标知识库" min-width="140" />
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column prop="strategy" label="应答策略" min-width="180" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:12px 0">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="loadData" small />
      </div>
    </section>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="isEdit ? '编辑映射' : '新增映射'" v-model="dialogVisible" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="关键词" required>
          <el-input v-model="form.keyword" placeholder="如：报销" />
        </el-form-item>
        <el-form-item label="目标知识库" required>
          <el-input v-model="form.kbName" placeholder="如：制度知识库" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="应答策略">
          <el-input v-model="form.strategy" placeholder="如：优先返回制度类来源" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthHeaders } from '../../api/index.js'

const mappings = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref({})
const editId = ref(null)

function headers() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

async function loadData() {
  loading.value = true
  try {
    let url = `/api/keyword-mappings?current=${page.value}&size=${size.value}`
    if (searchKeyword.value) url += `&keyword=${encodeURIComponent(searchKeyword.value)}`
    const resp = await fetch(url, { headers: headers() })
    const json = await resp.json()
    const data = json.data || {}
    mappings.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { keyword: '', kbName: '', priority: 100, strategy: '', enabled: 1 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.keyword || !form.value.kbName) {
    ElMessage.warning('关键词和目标知识库不能为空')
    return
  }
  saving.value = true
  try {
    const url = isEdit.value ? `/api/keyword-mappings/${editId.value}` : '/api/keyword-mappings'
    const method = isEdit.value ? 'PUT' : 'POST'
    const resp = await fetch(url, { method, headers: headers(), body: JSON.stringify(form.value) })
    if (resp.ok) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadData()
    }
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除关键词「${row.keyword}」的映射吗？`, '确认删除', { type: 'warning' })
  await fetch(`/api/keyword-mappings/${row.id}`, { method: 'DELETE', headers: headers() })
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => loadData())
</script>
```

关键变更：
- 删除 3 条 mock 数据 `const mappings = [...]`
- `onMounted` 调用 `loadData()` 从 `GET /api/keyword-mappings` 加载
- 新增弹窗表单 + 编辑 + 删除功能
- 表格列从 `intent/knowledgeBase` 改为 `kbName`，新增 `enabled` 状态列
- 支持关键词搜索和分页

- [ ] **Step 2: Commit**

```bash
git add enterprise-web/src/pages/admin/KeywordMappings.vue
git commit -m "feat: KeywordMappings 去 mock 对接真实 CRUD API

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: 编译验证

**Files:** 无，仅验证

- [ ] **Step 1: 编译协作服务**

```bash
mvn compile -pl enterprise-collaboration-service -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 验证前端无语法错误**

```bash
cd enterprise-web && npx vue-tsc --noEmit 2>&1 | tail -5 || echo "vue-tsc not available, skip"
```

Expected: 无新增错误。
```

---

## Verification Checklist

代码部署后验证：

1. 访问 `/admin/keyword-mappings` 页面，表格能加载数据库中的映射数据
2. 点击「新增映射」→ 填写表单 → 创建成功 → 列表刷新
3. 点击「编辑」→ 修改字段 → 保存成功
4. 点击「删除」→ 确认后删除成功
5. 搜索框输入关键词 → 列表过滤
6. 调用 `POST /api/keyword-mappings/match` 传入 `{"query":"我要报销"}` → 返回命中的映射按 priority 降序
