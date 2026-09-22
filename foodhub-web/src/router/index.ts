import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
    {
      path: '/', component: AppShell, children: [
        { path: '', name: 'discover', component: () => import('@/views/DiscoverView.vue') },
        { path: 'merchant/:id', name: 'merchant', component: () => import('@/views/MerchantDetailView.vue') },
        { path: 'community', name: 'community', component: () => import('@/views/CommunityView.vue') },
        { path: 'benefits', name: 'benefits', component: () => import('@/views/BenefitsView.vue') },
        { path: 'orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { auth: true } },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.auth && !localStorage.getItem('foodhub_token')) return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
