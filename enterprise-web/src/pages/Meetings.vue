<template>
  <div style="background:#fff;border-radius:10px;padding:16px">
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">会议列表</span>
      <el-button type="primary" @click="dlg=true">新建会议</el-button>
    </div>
    <el-table :data="meetings" stripe>
      <el-table-column prop="title" label="会议标题" min-width="160" />
      <el-table-column prop="room" label="会议室" width="120" />
      <el-table-column prop="date" label="日期" width="110" />
      <el-table-column prop="time" label="时间" width="140" />
      <el-table-column prop="attendees" label="参会人" width="80" />
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='已确认'?'success':'warning'" size="small">{{row.status}}</el-tag></template></el-table-column>
    </el-table>
    <el-dialog v-model="dlg" title="新建会议" width="450px"><el-form :model="f" label-width="70px">
      <el-form-item label="标题"><el-input v-model="f.title" /></el-form-item>
      <el-form-item label="会议室"><el-select v-model="f.room"><el-option v-for="r in rooms" :key="r" :label="r" :value="r"/></el-select></el-form-item>
      <el-form-item label="日期"><el-date-picker v-model="f.date" /></el-form-item>
      <el-form-item label="时间"><el-time-picker v-model="f.time" is-range /></el-form-item>
    </el-form><template #footer><el-button @click="dlg=false">取消</el-button><el-button type="primary" @click="dlg=false">创建</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const dlg=ref(false), f=ref({title:'',room:'',date:'',time:[]}), rooms=['A301(20人)','B102(10人)','C501(50人)','线上会议室']
const meetings=ref([
  {title:'Q2项目评审会',room:'A301(20人)',date:'2026-05-12',time:'14:00-16:00',attendees:8,status:'已确认'},
  {title:'技术方案讨论',room:'B102(10人)',date:'2026-05-12',time:'10:00-11:00',attendees:5,status:'已确认'},
  {title:'新人入职培训',room:'C501(50人)',date:'2026-05-13',time:'09:00-12:00',attendees:20,status:'待确认'},
])
</script>
