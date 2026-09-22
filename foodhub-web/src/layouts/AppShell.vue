<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Compass, Gift, House, MessageCircle, ReceiptText, Search, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const nav = [
  { path: '/', label: '发现', icon: House },
  { path: '/community', label: '社区', icon: MessageCircle },
  { path: '/benefits', label: '优惠', icon: Gift },
  { path: '/orders', label: '订单', icon: ReceiptText },
  { path: '/profile', label: '我的', icon: UserRound },
]
const activePath = computed(() => route.path === '/' || route.path.startsWith('/merchant/') ? '/' : route.path)
function search() {
  if (route.path !== '/') router.push({ path: '/', query: { focus: 'search' } })
  else document.querySelector<HTMLInputElement>('#discover-search')?.focus()
}
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <router-link class="brand" to="/" aria-label="FoodHub 首页">
        <span class="brand-mark"><Compass :size="23" /></span>
        <span>FoodHub</span>
      </router-link>
      <nav class="side-nav" aria-label="主导航">
        <router-link v-for="item in nav" :key="item.path" :to="item.path" :class="{ active: activePath === item.path }">
          <component :is="item.icon" :size="20" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
      <button class="account-chip" type="button" @click="router.push(auth.isLoggedIn ? '/profile' : '/login')">
        <span class="avatar">{{ auth.user?.username?.slice(0, 1).toUpperCase() || '访' }}</span>
        <span class="account-copy"><strong>{{ auth.user?.username || '登录 FoodHub' }}</strong><small>{{ auth.user ? '查看个人中心' : '收藏你的美味' }}</small></span>
      </button>
    </aside>

    <section class="app-column">
      <header class="topbar">
        <router-link class="mobile-brand" to="/"><span class="brand-mark"><Compass :size="20" /></span><strong>FoodHub</strong></router-link>
        <button class="quick-search" type="button" @click="search"><Search :size="17" /><span>搜索商户、菜品</span><kbd>⌘ K</kbd></button>
        <button class="top-avatar" type="button" @click="router.push(auth.isLoggedIn ? '/profile' : '/login')">{{ auth.user?.username?.slice(0, 1).toUpperCase() || '访' }}</button>
      </header>
      <main class="page-content"><router-view /></main>
    </section>

    <nav class="bottom-nav" aria-label="移动端导航">
      <router-link v-for="item in nav" :key="item.path" :to="item.path" :class="{ active: activePath === item.path }">
        <component :is="item.icon" :size="21" /><span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>
