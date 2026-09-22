import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('foodhub_token') || '')
  const user = ref<User | null>(JSON.parse(localStorage.getItem('foodhub_user') || 'null'))
  const isLoggedIn = computed(() => Boolean(token.value))

  function persist(nextToken: string, nextUser: User) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem('foodhub_token', nextToken)
    localStorage.setItem('foodhub_user', JSON.stringify(nextUser))
  }
  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem('foodhub_token')
    localStorage.removeItem('foodhub_user')
  }
  async function login(account: string, password: string) {
    const result = await authApi.login(account, password)
    persist(result.token, result.user)
  }
  async function register(username: string, password: string) {
    const result = await authApi.register(username, password)
    persist(result.token, result.user)
  }
  async function restore() {
    if (!token.value) return
    try { user.value = await authApi.me() } catch { clear() }
  }
  async function logout() {
    try { await authApi.logout() } finally { clear() }
  }
  return { token, user, isLoggedIn, login, register, restore, logout, clear }
})
