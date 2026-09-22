<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Clock3, Gift, TicketCheck, Zap } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { couponApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { Coupon, SeckillActivity } from '@/types'

const auth = useAuthStore()
const tab = ref<'coupon' | 'seckill'>('coupon')
const coupons = ref<Coupon[]>([])
const activities = ref<SeckillActivity[]>([])
const busyId = ref<number>()
const loading = ref(true)

async function load() {
  loading.value = true
  try { const [couponPage, activityPage] = await Promise.all([couponApi.list(), couponApi.activities()]); coupons.value = couponPage.items; activities.value = activityPage.items } finally { loading.value = false }
}
function requireLogin() { if (!auth.isLoggedIn) { ElMessage.info('请先登录，再领取优惠'); return false }; return true }
async function claim(coupon: Coupon) {
  if (!requireLogin()) return
  busyId.value = coupon.id
  try { await couponApi.claim(coupon.id); coupon.claimable = false; coupon.remainingStock--; ElMessage.success('优惠券已放入你的账户') } finally { busyId.value = undefined }
}
async function seckill(activity: SeckillActivity) {
  if (!requireLogin()) return
  busyId.value = activity.id
  try {
    const pathResult = await couponApi.path(activity.id)
    await couponApi.submit(activity.id, pathResult.path)
    ElMessage.success('抢购请求已提交，可到订单页查看结果')
  } finally { busyId.value = undefined }
}
function date(value: string) { return new Date(value).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) }
onMounted(load)
</script>

<template>
  <div class="content-width benefits-page">
    <section class="page-heading"><p class="eyebrow">BENEFITS</p><h1>把喜欢，变得更划算</h1><p>领取日常优惠，或者赶上限时好价。</p></section>
    <div class="segmented"><button :class="{ active: tab === 'coupon' }" @click="tab = 'coupon'"><Gift :size="17" />优惠券</button><button :class="{ active: tab === 'seckill' }" @click="tab = 'seckill'"><Zap :size="17" />限时秒杀</button></div>
    <div v-if="loading" class="coupon-grid"><div v-for="n in 4" :key="n" class="coupon-card skeleton-card"></div></div>
    <template v-else-if="tab === 'coupon'">
      <div v-if="coupons.length" class="coupon-grid"><article v-for="coupon in coupons" :key="coupon.id" class="coupon-card"><div class="coupon-value"><span>¥</span><strong>{{ Number(coupon.faceValue).toFixed(0) }}</strong><small>满 ¥{{ Number(coupon.minSpend).toFixed(0) }} 可用</small></div><div class="coupon-info"><span class="soft-label">商户专享</span><h3>{{ coupon.name }}</h3><p><Clock3 :size="14" />{{ date(coupon.validFrom) }} - {{ date(coupon.validUntil) }}</p><small>剩余 {{ coupon.remainingStock }} / {{ coupon.totalStock }}</small></div><button class="outline-button" :disabled="!coupon.claimable || busyId === coupon.id" @click="claim(coupon)">{{ coupon.claimable ? (busyId === coupon.id ? '领取中' : '领取') : '已领取' }}</button></article></div>
      <div v-else class="empty-state"><TicketCheck :size="28" /><h3>暂时没有可领优惠</h3><p>新的优惠正在路上。</p></div>
    </template>
    <template v-else>
      <div v-if="activities.length" class="seckill-list"><article v-for="activity in activities" :key="activity.id"><div class="seckill-icon"><Zap :size="24" /></div><div class="seckill-main"><span class="soft-label">{{ activity.status === 'ACTIVE' ? '正在进行' : activity.status }}</span><h3>{{ activity.title }}</h3><p><Clock3 :size="14" />{{ new Date(activity.startAt).toLocaleString('zh-CN') }} 开始</p><div class="stock-track"><span :style="{ width: `${Math.max(4, activity.remainingStock / activity.totalStock * 100)}%` }"></span></div><small>仅剩 {{ activity.remainingStock }} 份</small></div><div class="seckill-action"><strong>¥{{ Number(activity.seckillPrice).toFixed(2) }}</strong><button class="primary-button" :disabled="activity.remainingStock <= 0 || busyId === activity.id" @click="seckill(activity)">{{ activity.remainingStock > 0 ? (busyId === activity.id ? '提交中' : '马上抢') : '已抢完' }}</button></div></article></div>
      <div v-else class="empty-state"><Zap :size="28" /><h3>暂无秒杀活动</h3><p>活动开放后会第一时间出现在这里。</p></div>
    </template>
  </div>
</template>
