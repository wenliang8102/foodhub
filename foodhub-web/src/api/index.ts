import { deleteData, getData, postData } from './client'
import type { AuthResult, Category, Comment, Coupon, Food, Merchant, Order, Page, Post, SeckillActivity, SeckillResult, User } from '@/types'

export const authApi = {
  login: (account: string, password: string) => postData<AuthResult>('/auth/login', { account, password }),
  register: (username: string, password: string) => postData<AuthResult>('/auth/register', { username, password, phone: null, email: null }),
  me: () => getData<User>('/auth/me'),
  logout: () => postData<void>('/auth/logout'),
}
export const merchantApi = {
  categories: () => getData<Category[]>('/categories'),
  merchants: (params?: object) => getData<Page<Merchant>>('/merchants', params),
  merchant: (id: number) => getData<Merchant>(`/merchants/${id}`),
  foods: (params?: object) => getData<Page<Food>>('/foods', params),
}
export const socialApi = {
  posts: (params?: object) => getData<{ items: Post[]; page: number; pageSize: number; total: number }>('/posts', params),
  create: (payload: { content: string; imageUrls: string[]; merchantId?: number }) => postData<Post>('/posts', payload),
  remove: (id: number) => deleteData<void>(`/posts/${id}`),
  like: (id: number) => postData<void>(`/posts/${id}/likes`),
  unlike: (id: number) => deleteData<void>(`/posts/${id}/likes`),
  favorite: (id: number) => postData<void>(`/posts/${id}/favorite`),
  unfavorite: (id: number) => deleteData<void>(`/posts/${id}/favorite`),
  comments: (id: number) => getData<{ items: Comment[]; page: number; pageSize: number; total: number }>(`/posts/${id}/comments`),
  comment: (id: number, content: string) => postData<Comment>(`/posts/${id}/comments`, { content }),
}
export const couponApi = {
  list: () => getData<Page<Coupon>>('/coupons', { page: 1, size: 50 }),
  mine: () => getData<Page<Coupon>>('/coupons/mine'),
  claim: (id: number) => postData<void>(`/coupons/${id}/claim`),
  activities: () => getData<Page<SeckillActivity>>('/seckill/activities', { page: 1, size: 50 }),
  path: (id: number) => postData<{ path: string }>(`/seckill/activities/${id}/path`),
  submit: (id: number, path: string) => postData<SeckillResult>(`/seckill/activities/${id}/submit`, { path }),
  result: (id: number) => getData<SeckillResult>(`/seckill/activities/${id}/result`),
}
export const orderApi = {
  list: (params?: object) => getData<Page<Order>>('/orders', params),
  pay: (orderNo: string) => postData<Order>(`/orders/${orderNo}/pay`),
}
