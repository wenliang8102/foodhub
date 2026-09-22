<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Bookmark, Heart, ImagePlus, MessageCircle, Send, X } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { socialApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { Comment, Post } from '@/types'

const auth = useAuthStore()
const posts = ref<Post[]>([])
const loading = ref(true)
const publishing = ref(false)
const composerOpen = ref(false)
const draft = reactive({ content: '', imageUrl: '', merchantId: '' })
const commentPost = ref<Post>()
const comments = ref<Comment[]>([])
const commentText = ref('')

async function load() {
  loading.value = true
  try { posts.value = (await socialApi.posts({ page: 1, pageSize: 30 })).items } finally { loading.value = false }
}
function requireLogin() { if (!auth.isLoggedIn) { ElMessage.info('登录后即可参与互动'); return false }; return true }
async function publish() {
  if (!requireLogin() || !draft.content.trim()) return
  publishing.value = true
  try {
    await socialApi.create({ content: draft.content.trim(), imageUrls: draft.imageUrl.trim() ? [draft.imageUrl.trim()] : [], merchantId: draft.merchantId ? Number(draft.merchantId) : undefined })
    Object.assign(draft, { content: '', imageUrl: '', merchantId: '' }); composerOpen.value = false; ElMessage.success('动态已发布'); await load()
  } finally { publishing.value = false }
}
async function toggleLike(post: Post) {
  if (!requireLogin()) return
  await (post.liked ? socialApi.unlike(post.id) : socialApi.like(post.id)); post.liked = !post.liked; post.likeCount += post.liked ? 1 : -1
}
async function toggleFavorite(post: Post) {
  if (!requireLogin()) return
  await (post.favorited ? socialApi.unfavorite(post.id) : socialApi.favorite(post.id)); post.favorited = !post.favorited; post.favoriteCount += post.favorited ? 1 : -1
}
async function openComments(post: Post) { commentPost.value = post; comments.value = (await socialApi.comments(post.id)).items }
async function addComment() {
  if (!commentPost.value || !commentText.value.trim() || !requireLogin()) return
  const created = await socialApi.comment(commentPost.value.id, commentText.value.trim()); comments.value.push(created); commentPost.value.commentCount++; commentText.value = ''
}
onMounted(load)
</script>

<template>
  <div class="content-width community-page">
    <section class="page-heading row-heading"><div><p class="eyebrow">FOOD STORIES</p><h1>附近正在发生</h1><p>真实的一餐，也值得被好好记录。</p></div><button class="primary-button" @click="composerOpen = true"><ImagePlus :size="18" />发布动态</button></section>
    <section v-if="composerOpen" class="composer-panel"><div class="composer-head"><strong>分享此刻</strong><button class="icon-button" title="关闭" @click="composerOpen = false"><X :size="19" /></button></div><textarea v-model="draft.content" maxlength="500" placeholder="这家店怎么样？写下你的真实感受…"></textarea><div class="composer-fields"><input v-model="draft.imageUrl" placeholder="图片链接（选填）" /><input v-model="draft.merchantId" inputmode="numeric" placeholder="商户 ID（选填）" /></div><div class="composer-foot"><span>{{ draft.content.length }}/500</span><button class="primary-button" :disabled="publishing || !draft.content.trim()" @click="publish"><Send :size="16" />发布</button></div></section>

    <div v-if="loading" class="feed-column"><div v-for="n in 3" :key="n" class="post-card skeleton-card"></div></div>
    <div v-else-if="posts.length" class="feed-column">
      <article v-for="post in posts" :key="post.id" class="post-card">
        <header><span class="avatar">{{ (post.authorName || `用户${post.authorId}`).slice(0, 1) }}</span><div><strong>{{ post.authorName || `美食用户 ${post.authorId}` }}</strong><time>{{ new Date(post.publishedAt).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }}</time></div></header>
        <p class="post-content">{{ post.content }}</p>
        <div v-if="post.imageUrls?.length" :class="['post-images', { single: post.imageUrls.length === 1 }]" ><img v-for="url in post.imageUrls.slice(0, 4)" :key="url" :src="url" alt="动态配图" /></div>
        <footer><button :class="{ active: post.liked }" @click="toggleLike(post)"><Heart :size="19" :fill="post.liked ? 'currentColor' : 'none'" />{{ post.likeCount }}</button><button @click="openComments(post)"><MessageCircle :size="19" />{{ post.commentCount }}</button><button :class="{ active: post.favorited }" @click="toggleFavorite(post)"><Bookmark :size="19" :fill="post.favorited ? 'currentColor' : 'none'" />{{ post.favoriteCount }}</button></footer>
      </article>
    </div>
    <div v-else class="empty-state"><MessageCircle :size="28" /><h3>还没有人发布动态</h3><p>来成为第一个分享好味道的人。</p></div>

    <el-drawer v-model="commentPost" title="评论" direction="rtl" size="min(420px, 92vw)">
      <div class="comment-list"><article v-for="item in comments" :key="item.id"><span class="avatar">{{ (item.authorName || `用户${item.authorId}`).slice(0, 1) }}</span><div><strong>{{ item.authorName || `用户 ${item.authorId}` }}</strong><p>{{ item.content }}</p><time>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</time></div></article><p v-if="!comments.length" class="muted center">暂时没有评论</p></div>
      <div class="comment-input"><input v-model="commentText" placeholder="写下你的评论" @keyup.enter="addComment" /><button class="icon-button" title="发送" @click="addComment"><Send :size="18" /></button></div>
    </el-drawer>
  </div>
</template>
