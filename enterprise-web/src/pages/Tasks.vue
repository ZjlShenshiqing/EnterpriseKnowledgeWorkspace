<template>
  <div style="background:#fff;border-radius:10px;padding:16px">
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">任务看板</span>
      <el-button type="primary">新建任务</el-button>
    </div>
    <div style="display:flex;gap:16px;overflow-x:auto">
      <div v-for="col in columns" :key="col.title" style="min-width:240px;flex:1">
        <div style="background:#f5f7fa;border-radius:8px;padding:12px">
          <div style="font-weight:600;margin-bottom:10px;font-size:14px">{{ col.title }} <span style="color:#909399;font-weight:400">({{col.tasks.length}})</span></div>
          <div v-for="task in col.tasks" :key="task.id" style="background:#fff;padding:10px;margin-bottom:8px;border-radius:6px;cursor:pointer;border:1px solid #ebeef5" @click="detail=task;dlg=true">
            <div style="font-size:13px;font-weight:500">{{task.title}}</div>
            <div style="font-size:12px;color:#909399;margin-top:4px">{{task.assignee}} · {{task.dueDate}}</div>
            <el-tag size="small" style="margin-top:4px" :type="task.priority==='高'?'danger':'info'">{{task.priority}}</el-tag>
          </div>
        </div>
      </div>
    </div>
    <el-dialog v-model="dlg" title="任务详情" width="450px"><div v-if="detail" style="line-height:2">
      <p><b>标题：</b>{{detail.title}}</p><p><b>负责人：</b>{{detail.assignee}}</p>
      <p><b>截止：</b>{{detail.dueDate}}</p><p><b>状态：</b>{{detail.status}}</p>
    </div></el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const dlg=ref(false), detail=ref(null)
const columns=ref([
  {title:'待开始',tasks:[{id:1,title:'系统架构升级方案',assignee:'张三',dueDate:'2026-05-20',priority:'高',status:'待开始'},{id:2,title:'数据库迁移计划',assignee:'李四',dueDate:'2026-05-25',priority:'中',status:'待开始'}]},
  {title:'进行中',tasks:[{id:3,title:'知识库检索优化',assignee:'王五',dueDate:'2026-05-15',priority:'高',status:'进行中'}]},
  {title:'待确认',tasks:[{id:4,title:'前端性能优化',assignee:'赵六',dueDate:'2026-05-10',priority:'中',status:'待确认'}]},
  {title:'已完成',tasks:[{id:5,title:'文档上传流程优化',assignee:'张三',dueDate:'2026-05-05',priority:'中',status:'已完成'}]},
])
</script>
