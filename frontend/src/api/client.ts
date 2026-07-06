import axios, { type AxiosInstance } from 'axios'
import { authStore } from '@/store/authStore'
import { authApi } from '@/features/auth/api/authApi'

export const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

apiClient.interceptors.request.use((config) => {
  const token = authStore.getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
let failedQueue: Array<{ resolve: (v: string) => void; reject: (e: unknown) => void }> = []

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(p => error ? p.reject(error) : p.resolve(token!))
  failedQueue = []
}

apiClient.interceptors.response.use(
  r => r,
  async (error) => {
    const original = error.config
    // Endpoints de autenticação nunca devem disparar refresh: o 401 é credencial inválida
    const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']
    if (AUTH_ENDPOINTS.some(p => original?.url?.includes(p))) {
      return Promise.reject(error)
    }
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then(token => {
        original.headers.Authorization = `Bearer ${token}`
        return apiClient(original)
      })
    }
    original._retry = true
    isRefreshing = true
    const rt = authStore.getRefreshToken()
    if (!rt) {
      authStore.clear()
      window.location.href = '/login'
      return Promise.reject(error)
    }
    try {
      const tokens = await authApi.refresh(rt)
      const user = authStore.getUser()
      if (user) authStore.save(tokens, user)
      processQueue(null, tokens.accessToken)
      original.headers.Authorization = `Bearer ${tokens.accessToken}`
      return apiClient(original)
    } catch (refreshError) {
      processQueue(refreshError, null)
      authStore.clear()
      window.location.href = '/login'
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)
