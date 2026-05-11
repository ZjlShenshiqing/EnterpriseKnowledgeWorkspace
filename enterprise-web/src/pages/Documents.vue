<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <h2>文档管理</h2>
      <el-input v-model="keyword" placeholder="搜索文档标题" style="width:300px" @keyup.enter="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
    </div>
    <el-table :data="documents" stripe v-loading="loading" @row-click="showDetail" style="cursor:pointer">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="fileType" label="类型" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'info'">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切片数" width="80" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
    </el-table>
    <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next" @current-change="load" style="margin-top:16px;justify-content:center" />

    <el-dialog v-model="detailVisible" title="文档详情" width="600px">
      <div v-if="detail">
        <p><b>标题：</b>{{ detail.title }}</p>
        <p><b>类型：</b>{{ detail.fileType }}</p>
        <p><b>文件名：</b>{{ detail.fileName }}</p>
        <p><b>大小：</b>{{ (detail.fileSize/1024).toFixed(1) }} KB</p>
        <p><b>状态：</b>{{ detail.status }}</p>
        <p><b>摘要：</b>{{ detail.summary || '暂无' }}</p>
        <p><b>标签：</b>{{ detail.tags || '无' }}</p>
        <p v-if="detail.metadata"><b>元数据：</b>{{ detail.metadata }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocuments, searchDocuments, getDocument } from '../api'

const documents = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const keyword = ref('')
const detailVisible = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  try {
    const res = keyword.value
      ? await searchDocuments(keyword.value)
      : await getDocuments({ current: page.value, size: 20 })
    if (keyword.value) {
      documents.value = res.data.data || []
      total.value = documents.value.length
    } else {
      documents.value = res.data.data?.records || []
      total.value = res.data.data?.total || 0
    }
  } catch (e) {
    documents.value = mockDocs
    total.value = mockDocs.length
  }
  loading.value = false
}

function search() { page.value = 1; load() }

async function showDetail(row) {
  try {
    const res = await getDocument(row.id)
    detail.value = res.data.data
  } catch (e) { detail.value = row }
  detailVisible.value = true
}

const mockDocs = [
  { id: 1, title: '公司差旅报销制度', fileType: 'application/pdf', status: 'SUCCESS', chunkCount: 12, createdAt: '2026-03-15', fileName: 'travel.pdf', fileSize: 204800, summary: '差旅报销需提交审批单、发票和行程证明', tags: '制度,财务' },
  { id: 2, title: '微服务架构设计指南', fileType: 'application/msword', status: 'SUCCESS', chunkCount: 25, createdAt: '2026-04-02', fileName: 'microservice.docx', fileSize: 512000, summary: '微服务架构的核心概念和最佳实践', tags: '技术,架构' },
  { id: 3, title: '2026年度技术规划', fileType: 'application/pdf', status: 'PENDING', chunkCount: 0, createdAt: '2026-05-01', fileName: 'plan2026.pdf', fileSize: 1024000, summary: null, tags: '规划' },
]

onMounted(load)
</script>
