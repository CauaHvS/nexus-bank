import { useCallback, useEffect, useRef, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { BottomNav } from './BottomNav'
import { ToastContainer, type ToastMessage } from '@/components/ui/Toast'
import { useUnreadCount } from '@/features/notifications/useNotifications'

function useNewNotificationsToast() {
  const [toasts, setToasts] = useState<ToastMessage[]>([])
  const prevCount = useRef<number>(0)
  const initialized = useRef(false)

  const { data } = useUnreadCount()
  const count = data?.unreadCount ?? 0

  useEffect(() => {
    if (!initialized.current) {
      // Primeira leitura: apenas registra o valor base, sem exibir toast
      prevCount.current = count
      if (data !== undefined) initialized.current = true
      return
    }

    if (count > prevCount.current) {
      const diff = count - prevCount.current
      const id = crypto.randomUUID()
      setToasts((prev) => [
        ...prev,
        { id, text: `Voce tem ${diff} nova${diff > 1 ? 's' : ''} notificacao${diff > 1 ? 'es' : ''}` },
      ])
    }

    prevCount.current = count
  }, [count, data])

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  return { toasts, dismiss }
}

export function AppLayout() {
  const { toasts, dismiss } = useNewNotificationsToast()

  return (
    <div className="flex min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <main className="flex-1 px-4 py-6 pb-20 lg:pb-6 lg:px-8 max-w-5xl mx-auto w-full">
        <Outlet />
      </main>
      <BottomNav />
      <ToastContainer messages={toasts} onDismiss={dismiss} />
    </div>
  )
}
