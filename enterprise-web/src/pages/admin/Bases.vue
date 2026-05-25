<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">知识库管理</span>
      <el-button type="primary" @click="openCreate">创建知识库</el-button>
    </div>
    <el-table :data="bases" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="collectionName" label="Milvus集合" width="180" />
      <el-table-column prop="embeddingModel" label="嵌入模型" width="150" />
      <el-table-column prop="documentCount" label="文档数" width="80" />
      <el-table-column label="操作" width="120"><template #default><el-button size="small" type="danger">删除</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="创建知识库" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="输入知识库名称" />
        </el-form-item>
        <el-form-item label="Milvus集合名" required>
          <el-input v-model="form.collectionName" placeholder="输入 Milvus 集合名" />
        </el-form-item>
        <el-form-item label="嵌入模型">
          <el-input v-model="form.embeddingModel" placeholder="可选，留空使用默认模型" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate" :loading="creating">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getKnowledgeBases, createKnowledgeBase } from '../../api'

const bases = ref([])
const dialogVisible = ref(false)
const creating = ref(false)
const form = reactive({ name: '', collectionName: '', embeddingModel: '' })

function openCreate() {
  form.name = ''
  form.collectionName = ''
  form.embeddingModel = ''
  dialogVisible.value = true
}

async function doCreate() {
  if (!form.name.trim() || !form.collectionName.trim()) {
    ElMessage.warning('请填写名称和 Milvus 集合名')
    return
  }
  creating.value = true
  try {
    const res = await createKnowledgeBase({
      name: form.name.trim(),
      collectionName: form.collectionName.trim(),
      embeddingModel: form.embeddingModel.trim() || undefined
    })
    if (res.data && String(res.data.code) === '200') {
      ElMessage.success('创建成功')
      dialogVisible.value = false
      loadBases()
    } else {
      ElMessage.error(res.data?.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function loadBases() {
  try {
    const res = await getKnowledgeBases({ current: 1, size: 50 })
    const body = res.data
    if (body && String(body.code) === '200') {
      bases.value = body.data?.records || []
    }
  } catch(e) {
    // keep current data on error
  }
}

onMounted(() => {
  loadBases()
})
</script>
