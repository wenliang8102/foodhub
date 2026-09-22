<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Clock3, PackageOpen, ReceiptText, WalletCards } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { orderApi } from '@/api'
import type { Order } from '@/types'

const orders = ref<Order[]>([])
const loading = ref(true)
const paying = ref('')
const status = ref('ALL')
const tabs = [{ value: 'ALL', label: '全部' }, { value: 'PENDING_PAYMENT', label: '待支付' }, { value: 'PAID', label: '已支付' }, { value: 'CANCELLED', label: '已取消' }]
const filtered = computed(() => status.value === 'ALL' ? orders.value : orders.value.filter(item => item.status === status.value))
const statusText: Record<string, string> = { PENDING_PAYMENT: '待支付', PAID: '已支付', CANCELLED: '已取消', CREATED: '处理中', FAILED: '创建失败' }
async function load() { loading.value = true; try { orders.value = (await orderApi.list({ page: 1, size: 50 })).items } finally { loading.value = false } }
async function pay(order: Order) { paying.value = order.orderNo; try { await orderApi.pay(order.orderNo); ElMessage.success('支付成功'); await load() } finally { paying.value = '' } }
onMounted(load)
</script>

<template>
  <div class="content-width orders-page">
    <section class="page-heading"><p class="eyebrow">MY ORDERS</p><h1>我的订单</h1><p>查看抢购结果与订单状态。</p></section>
    <div class="order-tabs"><button v-for="item in tabs" :key="item.value" :class="{ active: status === item.value }" @click="status = item.value">{{ item.label }}</button></div>
    <div v-if="loading" class="order-list"><div v-for="n in 3" :key="n" class="order-card skeleton-card"></div></div>
    <div v-else-if="filtered.length" class="order-list"><article v-for="order in filtered" :key="order.orderNo" class="order-card"><header><span><ReceiptText :size="17" />订单 {{ order.orderNo }}</span><strong :class="`order-status status-${order.status.toLowerCase()}`">{{ statusText[order.status] || order.status }}</strong></header><div class="order-body"><div class="order-product"><span class="product-icon"><PackageOpen :size="23" /></span><div><h3>限时抢购商品 #{{ order.targetId }}</h3><p>数量 {{ order.quantity }} · 活动 #{{ order.activityId }}</p></div></div><div class="order-total"><small>实付金额</small><strong>¥{{ Number(order.totalAmount).toFixed(2) }}</strong></div></div><footer><span><Clock3 :size="14" />{{ new Date(order.createdAt).toLocaleString('zh-CN') }}</span><button v-if="order.status === 'PENDING_PAYMENT'" class="primary-button" :disabled="paying === order.orderNo" @click="pay(order)"><WalletCards :size="16" />{{ paying === order.orderNo ? '支付中' : '模拟支付' }}</button><span v-else-if="order.status === 'PAID'" class="paid-note"><CheckCircle2 :size="16" />支付完成</span></footer></article></div>
    <div v-else class="empty-state"><ReceiptText :size="29" /><h3>这里还没有订单</h3><p>去优惠页参与一次限时秒杀吧。</p><router-link class="primary-button" to="/benefits">去看看</router-link></div>
  </div>
</template>
