<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, MapPin, Search, Star, UtensilsCrossed } from 'lucide-vue-next'
import { merchantApi } from '@/api'
import type { Category, Food, Merchant } from '@/types'

const route = useRoute()
const keyword = ref('')
const activeCategory = ref<number | undefined>()
const categories = ref<Category[]>([])
const merchants = ref<Merchant[]>([])
const foods = ref<Food[]>([])
const loading = ref(true)
const fallbackImages = [
  'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=900&q=80',
  'https://images.unsplash.com/photo-1552566626-52f8b828add9?auto=format&fit=crop&w=900&q=80',
  'https://images.unsplash.com/photo-1569058242253-92a9c755a0ec?auto=format&fit=crop&w=900&q=80',
]
const filteredFoods = computed(() => foods.value.slice(0, 6))
let timer: number | undefined

async function load() {
  loading.value = true
  try {
    const [categoryData, merchantData, foodData] = await Promise.all([
      merchantApi.categories(),
      merchantApi.merchants({ keyword: keyword.value || undefined, categoryId: activeCategory.value, sort: 'rating', page: 1, size: 12 }),
      merchantApi.foods({ keyword: keyword.value || undefined, sort: 'sales', page: 1, size: 6 }),
    ])
    categories.value = categoryData
    merchants.value = merchantData.items
    foods.value = foodData.items
  } finally { loading.value = false }
}
function chooseCategory(id?: number) { activeCategory.value = id; load() }
watch(keyword, () => { window.clearTimeout(timer); timer = window.setTimeout(load, 350) })
onMounted(() => { load(); if (route.query.focus) window.setTimeout(() => document.querySelector<HTMLInputElement>('#discover-search')?.focus(), 100) })
</script>

<template>
  <div class="discover-page content-width">
    <section class="discover-heading">
      <div><p class="eyebrow">DISCOVER NEARBY</p><h1>今天，想吃点什么？</h1><p>发现附近认真做饭的人，也分享你刚遇见的好味道。</p></div>
      <div class="search-field"><Search :size="19" /><input id="discover-search" v-model="keyword" placeholder="搜索商户或菜品" /></div>
    </section>

    <div class="category-row">
      <button :class="{ active: activeCategory === undefined }" @click="chooseCategory()"><UtensilsCrossed :size="18" /><span>全部</span></button>
      <button v-for="item in categories" :key="item.id" :class="{ active: activeCategory === item.id }" @click="chooseCategory(item.id)"><span class="category-dot"></span><span>{{ item.name }}</span></button>
    </div>

    <section class="section-block">
      <div class="section-title"><div><h2>附近热门</h2><p>评分与人气都在线的小店</p></div><span>{{ merchants.length }} 家结果</span></div>
      <div v-if="loading" class="merchant-grid"><div v-for="n in 6" :key="n" class="merchant-card skeleton-card"></div></div>
      <div v-else-if="merchants.length" class="merchant-grid">
        <router-link v-for="(merchant, index) in merchants" :key="merchant.id" class="merchant-card" :to="`/merchant/${merchant.id}`">
          <div class="merchant-image"><img :src="fallbackImages[index % fallbackImages.length]" :alt="merchant.name" /><span :class="['status-pill', { closed: merchant.businessStatus !== 'OPEN' }]">{{ merchant.businessStatus === 'OPEN' ? '营业中' : '休息中' }}</span></div>
          <div class="merchant-body"><div class="merchant-name"><h3>{{ merchant.name }}</h3><ArrowRight :size="18" /></div><p><MapPin :size="14" />{{ merchant.address || merchant.categoryName }}</p><div class="merchant-meta"><span><Star :size="15" fill="currentColor" />{{ Number(merchant.rating || 0).toFixed(1) }}</span><span>月售 {{ merchant.salesCount || 0 }}</span><span>{{ merchant.categoryName }}</span></div></div>
        </router-link>
      </div>
      <div v-else class="empty-state"><UtensilsCrossed :size="28" /><h3>暂时没找到匹配商户</h3><p>换个关键词或分类试试。</p></div>
    </section>

    <section v-if="filteredFoods.length" class="section-block food-section">
      <div class="section-title"><div><h2>大家都在点</h2><p>按近期销量为你挑选</p></div></div>
      <div class="food-strip"><article v-for="food in filteredFoods" :key="food.id" class="food-tile"><img :src="food.imageUrl || 'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=600&q=80'" :alt="food.name" /><div><h3>{{ food.name }}</h3><p>{{ food.merchantName }}</p><strong>¥{{ Number(food.price).toFixed(2) }}</strong></div></article></div>
    </section>
  </div>
</template>
