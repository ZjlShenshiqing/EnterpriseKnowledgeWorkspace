<template>
  <div>
    <h2 style="margin-bottom:16px">知识库设置</h2>
    <el-table :data="bases" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="collectionName" label="Milvus 集合" width="200" />
      <el-table-column prop="embeddingModel" label="嵌入模型" width="150" />
      <el-table-column prop="documentCount" label="文档数" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getKnowledgeBases } from '../api'

const bases = ref([])
const loading = ref(false)
onMounted(async () => {
  loading.value = true
  try {
    const res = await getKnowledgeBases({ current: 1, size: 50 })
    bases.value = res.data.data?.records || []
  } catch(e) { bases.value = mockBases }
  loading.value = false
})

const mockBases = [
  { id: 1, name: '技术文档库', collectionName: 'tech_docs', embeddingModel: 'deepseek-chat', documentCount: 45, createdAt: '2026-01-15' },
  { id: 2, name: '公司制度库', collectionName: 'company_policy', embeddingModel: '', documentCount: 12, createdAt: '2026-02-20' },
]
</script>
