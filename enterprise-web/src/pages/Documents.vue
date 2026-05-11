<template>
  <div style="background:#fff;border-radius:10px;padding:16px">
    <div style="display:flex;gap:12px;margin-bottom:16px">
      <el-input v-model="keyword" placeholder="搜索文档标题" style="width:320px" @keyup.enter="search" :prefix-icon="Search" clearable />
      <el-button type="primary" @click="search">搜索</el-button>
    </div>
    <el-table :data="docs" stripe @row-click="showDetail" style="cursor:pointer">
      <el-table-column prop="title" label="文档标题" min-width="200" />
      <el-table-column prop="fileType" label="类型" width="140" />
      <el-table-column prop="status" label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'info'" size="small">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
    </el-table>
    <el-pagination v-if="total>20" v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next" @current-change="load" style="margin-top:16px;justify-content:center" />
    <el-dialog v-model="detailVisible" title="文档详情" width="550px">
      <div v-if="detail" style="line-height:2">
        <p><b>标题：</b>{{ detail.title }}</p><p><b>类型：</b>{{ detail.fileType }}</p>
        <p><b>文件：</b>{{ detail.fileName }}</p><p><b>大小：</b>{{ (detail.fileSize/1024).toFixed(1) }} KB</p>
        <p><b>状态：</b>{{ detail.status }}</p><p><b>摘要：</b>{{ detail.summary || '暂无' }}</p>
        <p><b>标签：</b>{{ detail.tags || '无' }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocuments, searchDocuments, getDocument } from '../api'
import { Search } from '@element-plus/icons-vue'

const docs = ref([]); const keyword = ref(''); const page = ref(1); const total = ref(0)
const detailVisible = ref(false); const detail = ref(null)

async function load() {
  try {
    const res = keyword.value ? await searchDocuments(keyword.value) : await getDocuments({ current: page.value, size: 20 })
    docs.value = keyword.value ? (res.data.data||[]) : (res.data.data?.records||[])
    total.value = keyword.value ? docs.value.length : (res.data.data?.total||0)
  } catch(e) { docs.value = mock; total.value = mock.length }
}
async function search() { page.value=1; await load() }
async function showDetail(row) { try { detail.value = (await getDocument(row.id)).data.data } catch(e) { detail.value=row }; detailVisible.value=true }
onMounted(load)

const mock = [
  { id:1,title:'公司差旅报销制度',fileType:'application/pdf',status:'SUCCESS',createdAt:'2026-03-15',fileName:'travel.pdf',fileSize:204800,summary:'差旅报销需提交审批单、发票和行程证明' },
  { id:2,title:'微服务架构设计指南',fileType:'application/msword',status:'SUCCESS',createdAt:'2026-04-02',fileName:'ms.docx',fileSize:512000,summary:'微服务架构的核心概念和最佳实践' },
  { id:3,title:'2026年度技术规划',fileType:'application/pdf',status:'PENDING',createdAt:'2026-05-01',fileName:'plan.pdf',fileSize:1024000,summary:null },
]
</script>
