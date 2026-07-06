import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getNotifications,
  getUnreadCount,
  markAllAsRead,
  markAsRead,
} from './notificationsApi'

const NOTIFICATIONS_KEY = 'notifications'
const UNREAD_COUNT_KEY = [NOTIFICATIONS_KEY, 'unread-count'] as const

export function useNotifications(page = 0) {
  return useQuery({
    queryKey: [NOTIFICATIONS_KEY, page],
    queryFn: () => getNotifications(page),
    refetchInterval: 30_000,
    staleTime: 15_000,
  })
}

export function useUnreadCount() {
  return useQuery({
    queryKey: UNREAD_COUNT_KEY,
    queryFn: getUnreadCount,
    refetchInterval: 30_000,
    staleTime: 15_000,
  })
}

export function useMarkAsRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => markAsRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [NOTIFICATIONS_KEY] })
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
    },
  })
}

export function useMarkAllAsRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: markAllAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [NOTIFICATIONS_KEY] })
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
    },
  })
}
