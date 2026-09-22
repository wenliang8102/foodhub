<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Compass, Eye, EyeOff } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const visible = ref(false)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (form.username.trim().length < 3 || form.password.length < 6) {
    ElMessage.warning('账号至少 3 位，密码至少 6 位')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') await auth.login(form.username.trim(), form.password)
    else await auth.register(form.username.trim(), form.password)
    ElMessage.success(mode.value === 'login' ? '欢迎回来' : '账号创建成功')
    router.replace(String(route.query.redirect || '/'))
  } finally { loading.value = false }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-visual">
      <router-link class="auth-brand" to="/"><span class="brand-mark"><Compass :size="23" /></span><strong>FoodHub</strong></router-link>
      <div class="auth-caption"><p>今晚吃点好的</p><span>从街角小店，到刚刚出炉的生活灵感。</span></div>
    </section>
    <section class="auth-panel">
      <button class="icon-button auth-back" type="button" title="返回" @click="router.back()"><ArrowLeft :size="20" /></button>
      <form class="auth-form" @submit.prevent="submit">
        <div><p class="eyebrow">WELCOME TO FOODHUB</p><h1>{{ mode === 'login' ? '很高兴再见到你' : '创建你的美食档案' }}</h1><p class="muted">{{ mode === 'login' ? '登录后领取优惠、参与秒杀并管理订单。' : '只需账号和密码，马上开始探索。' }}</p></div>
        <label>账号<input v-model="form.username" autocomplete="username" placeholder="输入用户名" /></label>
        <label>密码<span class="password-input"><input v-model="form.password" :type="visible ? 'text' : 'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 6 位" /><button type="button" :title="visible ? '隐藏密码' : '显示密码'" @click="visible = !visible"><component :is="visible ? EyeOff : Eye" :size="18" /></button></span></label>
        <button class="primary-button auth-submit" type="submit" :disabled="loading">{{ loading ? '请稍候…' : mode === 'login' ? '登录' : '创建账号' }}</button>
        <p class="auth-switch">{{ mode === 'login' ? '第一次来？' : '已有账号？' }} <button type="button" @click="mode = mode === 'login' ? 'register' : 'login'">{{ mode === 'login' ? '立即注册' : '返回登录' }}</button></p>
      </form>
    </section>
  </main>
</template>
