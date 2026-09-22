export interface Page<T> { items: T[]; page: number; size: number; total: number }
export interface User { id: number; username: string; phone?: string; email?: string; role: 'USER' | 'MERCHANT' | 'ADMIN' }
export interface AuthResult { token: string; user: User }
export interface Category { id: number; name: string; icon?: string; sortOrder?: number }
export interface Merchant {
  id: number; ownerUserId: number; categoryId: number; categoryName: string; name: string; address: string
  longitude?: number; latitude?: number; rating: number; salesCount: number; businessStatus: string; status: string
  createdAt?: string; updatedAt?: string
}
export interface Food {
  id: number; merchantId: number; merchantName: string; name: string; price: number; imageUrl?: string
  salesCount: number; onSale: boolean; status: string; createdAt?: string; updatedAt?: string
}
export interface Post {
  id: number; authorId: number; authorName?: string; content: string; imageUrls: string[]; merchantId?: number
  publishedAt: string; likeCount: number; favoriteCount: number; commentCount: number; liked: boolean; favorited: boolean
}
export interface Comment { id: number; postId?: number; authorId: number; authorName?: string; content: string; createdAt: string }
export interface Coupon {
  id: number; merchantId: number; name: string; faceValue: number; minSpend: number; totalStock: number
  remainingStock: number; validFrom: string; validUntil: string; status: string; claimable: boolean
}
export interface SeckillActivity {
  id: number; merchantId: number; targetType: string; targetId: number; title: string; seckillPrice: number
  totalStock: number; remainingStock: number; startAt: string; endAt: string; status: string; stockReady: boolean
}
export interface SeckillResult { requestId: string; activityId: number; status: string; orderNo?: string; failureCode?: string; createdAt: string }
export interface Order {
  id: number; orderNo: string; requestId: string; userId: number; activityId: number; targetType: string; targetId: number
  quantity: number; unitPrice: number; totalAmount: number; status: string; paymentExpiresAt?: string
  paidAt?: string; cancelledAt?: string; createdAt: string
}
