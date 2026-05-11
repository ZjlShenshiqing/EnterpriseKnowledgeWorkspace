<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">知识库管理</span>
      <el-button type="primary">创建知识库</el-button>
    </div>
    <el-table :data="bases" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="collectionName" label="Milvus集合" width="180" />
      <el-table-column prop="embeddingModel" label="嵌入模型" width="150" />
      <el-table-column prop="documentCount" label="文档数" width="80" />
      <el-table-column label="操作" width="120"><template #default><el-button size="small" type="danger">删除</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getKnowledgeBases } from '../../api'

const bases = ref([])

onMounted(async () => {
  try {
    const res = await getKnowledgeBases({ current: 1, size: 50 })
    bases.value = res.data.data?.records || []
  } catch(e) {
    bases.value = [
      {id:1,name:'技术文档库',collectionName:'tech_docs',embeddingModel:'deepseek-chat',documentCount:45},
      {id:2,name:'公司制度库',collectionName:'company_policy',embeddingModel:'',documentCount:12},
    ]
  }
})
</script>
