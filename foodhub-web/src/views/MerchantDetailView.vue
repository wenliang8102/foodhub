<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, MapPin, ShoppingBag, Star } from 'lucide-vue-next'
import { merchantApi } from '@/api'
import type { Food, Merchant } from '@/types'

const route = useRoute()
const router = useRouter()
const merchant = ref<Merchant>()
const foods = ref<Food[]>([])
const loading = ref(true)
onMounted(async () => {
  try {
    const id = Number(route.params.id)
    const [merchantData, foodData] = await Promise.all([merchantApi.merchant(id), merchantApi.foods({ merchantId: id, sort: 'sales', page: 1, size: 50 })])
    merchant.value = merchantData; foods.value = foodData.items
  } finally { loading.value = false }
})
</script>

<template>
  <div class="merchant-detail">
    <div class="merchant-cover"><img src="https://images.unsplash.com/photo-1515003197210-e0cd71810b5f?auto=format&fit=crop&w=1800&q=85" alt="餐厅与菜品" /><button class="floating-back" title="返回" @click="router.back()"><ArrowLeft :size="20" /></button></div>
    <div v-if="merchant" class="content-width merchant-detail-body">
      <section class="merchant-intro"><div><span class="status-pill">{{ merchant.businessStatus === 'OPEN' ? '营业中' : '休息中' }}</span><h1>{{ merchant.name }}</h1><p><MapPin :size="16" />{{ merchant.address }}</p></div><div class="rating-block"><strong>{{ Number(merchant.rating || 0).toFixed(1) }}</strong><span><Star :size="14" fill="currentColor" /> 用户评分</span></div></section>
      <section class="menu-section"><div class="section-title"><div><h2>本店菜单</h2><p>{{ foods.length }} 道在售菜品</p></div></div><div v-if="foods.length" class="menu-list"><article v-for="food in foods" :key="food.id" class="menu-item"><img :src="food.imageUrl || 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=500&q=80'" :alt="food.name" /><div class="menu-copy"><h3>{{ food.name }}</h3><p>已售 {{ food.salesCount || 0 }} 份</p><strong>¥{{ Number(food.price).toFixed(2) }}</strong></div><button class="round-action" title="查看优惠" @click="router.push('/benefits')"><ShoppingBag :size="18" /></button></article></div><div v-else-if="!loading" class="empty-state"><h3>商家正在准备菜单</h3><p>稍后再来看看。</p></div></section>
    </div>
  </div>
</template>
