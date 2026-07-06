import axios from 'axios'
import { authStore } from '@/store/authStore'

const notificationsClient = axios.create({
  baseURL: import.meta.env.VITE_NOTIFICATIONS_URL ?? 'http://localhost:8081',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

notificationsClient.interceptors.request.use((config) => {
  const token = authStore.getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

notificationsClient.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401) {
      authStore.clear()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export type NotificationType = 'TRANSFER_COMPLETED' | 'TRANSFER_FAILED' | 'ACCOUNT_OPENED'

export interface NotificationResponse {
  id: string
  userId: string
  type: NotificationType
  title: string
  body: string
  read: boolean
  createdAt: string
}

export interface NotificationListResponse {
  content: NotificationResponse[]
  unreadCount: number
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UnreadCountResponse {
  unreadCount: number
}

export async function getNotifications(
  page = 0,
  size = 20,
): Promise<NotificationListResponse> {
  const response = await notificationsClient.get<NotificationListResponse>(
    '/notifications',
    { params: { page, size } },
  )
  return response.data
}

export async function getUnreadCount(): Promise<UnreadCountResponse> {
  const response = await notificationsClient.get<UnreadCountResponse>(
    '/notifications/unread-count',
  )
  return response.data
}

export async function markAsRead(id: string): Promise<NotificationResponse> {
  const response = await notificationsClient.patch<NotificationResponse>(
    `/notifications/${id}/read`,
  )
  return response.data
}

export async function markAllAsRead(): Promise<void> {
  await notificationsClient.patch('/notifications/read-all')
}
