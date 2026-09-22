import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

interface ApiEnvelope<T> { code: string; message: string; data: T }

export const api = axios.create({ baseURL: '/api', timeout: 12000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('foodhub_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const message = error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '服务暂时不可用')
    if (error.response?.status === 401) {
      localStorage.removeItem('foodhub_token')
      localStorage.removeItem('foodhub_user')
      if (!location.pathname.startsWith('/login')) location.assign(`/login?redirect=${encodeURIComponent(location.pathname)}`)
    }
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export async function getData<T>(url: string, params?: object): Promise<T> {
  const response = await api.get<ApiEnvelope<T>>(url, { params })
  return response.data.data
}

export async function postData<T>(url: string, data?: object): Promise<T> {
  const response = await api.post<ApiEnvelope<T>>(url, data)
  return response.data.data
}

export async function deleteData<T>(url: string): Promise<T> {
  const response = await api.delete<ApiEnvelope<T>>(url)
  return response.data.data
}
