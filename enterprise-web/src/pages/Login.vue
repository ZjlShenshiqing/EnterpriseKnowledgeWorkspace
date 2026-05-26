<template>
  <div class="login-page">
    <div class="bg-shapes">
      <div v-for="s in shapes" :key="s.i" :style="s.style" class="shape" />
    </div>
    <div class="login-card" ref="cardRef">
      <div class="logo-area">
        <div class="logo-icon pulse-ring">
          <span class="logo-text">E</span>
        </div>
        <div class="title">企业智能工作平台</div>
        <div class="subtitle">Enterprise Work Platform</div>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" class="login-input" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password @keyup.enter="login" class="login-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" @click="login" :loading="loading">
            <span v-if="!loading">登 录</span>
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-hint">账号由管理员创建，如需开通请联系系统管理员</div>
      <div class="footer-text">Enterprise Knowledge Workspace</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { saveStoredAuth } from '../api/index.js'

const router = useRouter()
const loading = ref(false)
const formRef = ref(null)
const cardRef = ref(null)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const shapes = Array.from({length:6}, (_,i) => ({
  i, style: { left: (15+i*13)%80+'%', top: (10+i*15)%80+'%', animationDelay: i*0.8+'s', animationDuration: (6+i*2)+'s' }
}))

onMounted(() => {
  const tip = sessionStorage.getItem('login_redirect_message')
  if (tip) {
    sessionStorage.removeItem('login_redirect_message')
    ElMessage.warning(tip)
  }
})

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
    const result = await resp.json()
    if (resp.ok && (result.code === 200 || result.code === '200')) {
      const data = result.data || {}
      saveStoredAuth({
        id: data.userId,
        username: data.username,
        realName: data.realName || data.username,
        isAdmin: !!data.isAdmin,
        departmentId: data.deptId,
        token: data.token || data.accessToken
      })
      ElMessage.success('登录成功')
      cardRef.value?.classList.add('card-out')
      setTimeout(() => router.push('/'), 300)
    } else {
      shakeCard()
      ElMessage.error(result.message || '用户名或密码错误')
    }
  } catch (e) {
    ElMessage.error('无法连接登录服务，请确认网关已启动（端口 8086）')
  }
  loading.value = false
}

function shakeCard() { cardRef.value?.classList.add('shake'); setTimeout(() => cardRef.value?.classList.remove('shake'), 500) }
</script>

<style>
.login-page {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #e8f0fe 0%, #f0f4ff 30%, #f5f3ff 60%, #e8f0fe 100%);
  overflow: hidden; position: relative;
}
.bg-shapes { position: absolute; inset: 0; pointer-events: none; }
.shape {
  position: absolute; width: 60px; height: 60px; border-radius: 18px;
  background: linear-gradient(135deg, rgba(51,112,255,0.06), rgba(51,112,255,0.12));
  animation: floatShape 8s ease-in-out infinite;
}
@keyframes floatShape {
  0%, 100% { transform: translateY(0) rotate(0deg) scale(1); }
  33% { transform: translateY(-30px) rotate(3deg) scale(1.05); }
  66% { transform: translateY(20px) rotate(-2deg) scale(0.95); }
}
.login-card {
  width: 420px; background: rgba(255,255,255,0.92); backdrop-filter: blur(20px);
  border-radius: 20px; padding: 48px 44px; box-shadow: 0 20px 60px rgba(0,0,0,0.08), 0 0 1px rgba(0,0,0,0.05);
  animation: cardIn .5s cubic-bezier(.22,.61,.36,1); position: relative; z-index: 1;
}
@keyframes cardIn { from { opacity: 0; transform: translateY(24px) scale(.96); } to { opacity:1; transform: none; } }
.card-out { animation: cardOut .3s ease-in forwards; }
@keyframes cardOut { to { opacity:0; transform: translateY(-16px) scale(.97); } }

.shake { animation: shakeAnim .4s ease !important; }
@keyframes shakeAnim { 0%,100%{transform:translateX(0)} 20%{transform:translateX(-8px)} 40%{transform:translateX(8px)} 60%{transform:translateX(-6px)} 80%{transform:translateX(6px)} }

.logo-area { text-align: center; margin-bottom: 36px; }
.logo-icon {
  width: 56px; height: 56px; border-radius: 16px; background: linear-gradient(135deg, #3370ff, #5b8cff);
  margin: 0 auto 16px; display: flex; align-items: center; justify-content: center; position: relative;
}
.logo-text { font-size: 26px; font-weight: 700; color: #fff; position: relative; z-index: 1; }
.pulse-ring::after {
  content: ''; position: absolute; inset: -6px; border-radius: 22px;
  border: 2px solid rgba(51,112,255,0.2); animation: pulseRing 2s ease-out infinite;
}
@keyframes pulseRing { 0%{transform:scale(1);opacity:1} 100%{transform:scale(1.4);opacity:0} }
.title { font-size: 26px; font-weight: 700; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-tertiary); margin-top: 6px; letter-spacing: .5px; }

.login-input .el-input__wrapper {
  border-radius: 12px; transition: all .25s;
  box-shadow: none !important;
  border: 1px solid #e5e6eb;
}
.login-input .el-input__wrapper:hover { border-color: #c0c4cc; }
.login-input .el-input__wrapper.is-focus { border-color: var(--brand-500) !important; box-shadow: none !important; }
.login-input .el-input__inner:-webkit-autofill,
.login-input .el-input__inner:-webkit-autofill:hover,
.login-input .el-input__inner:-webkit-autofill:focus {
  -webkit-box-shadow: 0 0 0 1000px transparent inset !important;
  -webkit-text-fill-color: var(--text-primary) !important;
  transition: background-color 9999s ease-in-out 0s;
}

.login-btn {
  width: 100%; border-radius: 12px; height: 46px; font-size: 16px; letter-spacing: 4px;
  transition: all .3s; margin-top: 4px;
}
.login-btn:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(51,112,255,0.3); }

.register-hint { text-align: center; margin-top: 8px; font-size: 12px; color: #9ca3af; }
.footer-text { text-align: center; color: #c0c4cc; font-size: 12px; margin-top: 28px; }
</style>
