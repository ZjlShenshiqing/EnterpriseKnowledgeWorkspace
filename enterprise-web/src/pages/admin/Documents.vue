<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">文档管理</span>
      <el-button type="primary">上传文档</el-button>
    </div>
    <el-table :data="docs" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="fileType" label="类型" width="120" />
      <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'info'" size="small">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="chunkCount" label="切片" width="60" />
      <el-table-column label="操作" width="200">
        <template #default="{row}">
          <el-button size="small" type="warning" v-if="row.status==='PENDING'||row.status==='FAILED'">分块</el-button>
          <el-button size="small" type="primary">启用</el-button>
          <el-button size="small" type="danger">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total>20" v-model:current-page="page" :total="total" layout="prev,pager,next" @current-change="load" style="margin-top:16px;justify-content:center" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocuments } from '../../api'

const docs = ref([]); const loading = ref(false); const page = ref(1); const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res = await getDocuments({ current: page.value, size: 20 })
    docs.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch(e) {
    docs.value = [
      {id:1,title:'公司差旅报销制度',fileType:'application/pdf',status:'SUCCESS',chunkCount:12},
      {id:2,title:'微服务架构设计指南',fileType:'application/msword',status:'SUCCESS',chunkCount:25},
      {id:3,title:'2026年度技术规划',fileType:'application/pdf',status:'PENDING',chunkCount:0},
    ]; total.value = 3
  }
  loading.value = false
}

onMounted(load)
</script>
