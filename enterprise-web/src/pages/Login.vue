<template>
  <div style="height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#f5f7fa 0%,#e4e7ed 100%)">
    <div style="width:400px;background:#fff;border-radius:16px;padding:48px 40px;box-shadow:0 8px 40px rgba(0,0,0,0.08)">
      <div style="text-align:center;margin-bottom:36px">
        <div style="width:48px;height:48px;border-radius:12px;background:var(--brand-500);margin:0 auto 12px;display:flex;align-items:center;justify-content:center;font-size:22px;font-weight:700;color:#fff">E</div>
        <div style="font-size:24px;font-weight:700;color:var(--text-primary)">企业智能工作平台</div>
        <div style="font-size:14px;color:#909399;margin-top:6px">Enterprise Work Platform</div>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password @keyup.enter="login" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%" @click="login" :loading="loading">登 录</el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;margin-top:8px">
        <el-button link size="small" @click="demoLogin">演示登录</el-button>
      </div>
      <div style="text-align:center;color:#c0c4cc;font-size:12px;margin-top:24px">
        Demo · Enterprise Knowledge Workspace
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const formRef = ref(null)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function saveAuth(user) {
  localStorage.setItem('token', user.token || 'demo-token')
  localStorage.setItem('user', JSON.stringify(user))
}

async function login() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const resp = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: form.username, password: form.password })
    })
    if (resp.ok) {
      const data = await resp.json()
      saveAuth({ id: data.data?.userId || 1, username: form.username, realName: form.username, isAdmin: true, token: data.data?.token || '' })
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error('用户名或密码错误')
    }
  } catch (e) {
    ElMessage.warning('后端服务未启动，使用演示模式')
    demoLogin()
  }
  loading.value = false
}

function demoLogin() {
  saveAuth({ id: 1, username: 'admin', realName: '管理员', isAdmin: true, departmentId: 1, projectId: null })
  router.push('/')
}
</script>
