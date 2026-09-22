<script setup lang="ts">
import { Bookmark, ChevronRight, Gift, LogOut, ReceiptText, Settings, UserRound } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
async function logout() { await auth.logout(); router.replace('/') }
</script>

<template>
  <div class="content-width profile-page">
    <section v-if="auth.isLoggedIn" class="profile-hero"><span class="profile-avatar">{{ auth.user?.username.slice(0, 1).toUpperCase() }}</span><div><p class="eyebrow">MY FOODHUB</p><h1>{{ auth.user?.username }}</h1><p>{{ auth.user?.email || auth.user?.phone || '记录每一次值得回味的相遇' }}</p></div><span class="role-badge">{{ auth.user?.role }}</span></section>
    <section v-else class="profile-hero guest"><span class="profile-avatar"><UserRound :size="31" /></span><div><p class="eyebrow">MY FOODHUB</p><h1>登录后继续</h1><p>你的优惠、订单和收藏会保存在这里。</p></div><router-link class="primary-button" to="/login">登录 / 注册</router-link></section>
    <div class="profile-grid"><section><h2>常用功能</h2><div class="settings-list"><router-link to="/orders"><span class="settings-icon coral"><ReceiptText :size="20" /></span><div><strong>我的订单</strong><small>查看订单与支付状态</small></div><ChevronRight :size="18" /></router-link><router-link to="/benefits"><span class="settings-icon green"><Gift :size="20" /></span><div><strong>优惠与秒杀</strong><small>领取商户优惠券</small></div><ChevronRight :size="18" /></router-link><router-link to="/community"><span class="settings-icon yellow"><Bookmark :size="20" /></span><div><strong>社区收藏</strong><small>回看喜欢的美食动态</small></div><ChevronRight :size="18" /></router-link></div></section><section><h2>账户</h2><div class="settings-list"><button><span class="settings-icon gray"><Settings :size="20" /></span><div><strong>账户信息</strong><small>ID {{ auth.user?.id || '登录后可见' }}</small></div><ChevronRight :size="18" /></button><button v-if="auth.isLoggedIn" class="danger-row" @click="logout"><span class="settings-icon gray"><LogOut :size="20" /></span><div><strong>退出登录</strong><small>清除当前设备的登录状态</small></div><ChevronRight :size="18" /></button></div></section></div>
  </div>
</template>
