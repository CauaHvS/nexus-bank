import { useState } from 'react'
import { cn } from '@/lib/utils'
import {
  useMarkAllAsRead,
  useMarkAsRead,
  useNotifications,
  useUnreadCount,
} from './useNotifications'
import type { NotificationResponse, NotificationType } from './notificationsApi'

// ---- Utilitarios --------------------------------------------------------

function formatRelativeDate(iso: string): string {
  const now = Date.now()
  const then = new Date(iso).getTime()
  const diffMs = now - then

  if (diffMs < 0) return 'agora'

  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return 'agora ha pouco'

  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `ha ${diffMin} min`

  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `ha ${diffHour}h`

  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `ha ${diffDay} dia${diffDay > 1 ? 's' : ''}`

  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

// ---- Icones por tipo ----------------------------------------------------

interface IconConfig {
  bg: string
  color: string
  label: string
  path: string
}

const TYPE_CONFIG: Record<NotificationType, IconConfig> = {
  TRANSFER_COMPLETED: {
    bg: 'bg-blue-100 dark:bg-blue-900/30',
    color: '#2563eb',
    label: 'Transferencia concluida',
    path: 'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z',
  },
  TRANSFER_FAILED: {
    bg: 'bg-red-100 dark:bg-red-900/30',
    color: '#dc2626',
    label: 'Transferencia falhou',
    path: 'M18 6L6 18M6 6l12 12',
  },
  ACCOUNT_OPENED: {
    bg: 'bg-amber-100 dark:bg-amber-900/30',
    color: '#d97706',
    label: 'Conta aberta',
    path: 'M22 11.08V12a10 10 0 11-5.93-9.14M22 4L12 14.01l-3-3',
  },
}

function NotificationIcon({ type }: { type: NotificationType }) {
  const config = TYPE_CONFIG[type]
  return (
    <div
      className={cn('w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0', config.bg)}
      aria-hidden="true"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={config.color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d={config.path} />
      </svg>
    </div>
  )
}

// ---- Skeleton -----------------------------------------------------------

function NotificationSkeleton() {
  return (
    <div className="flex items-start gap-3 px-5 py-4 border-b border-gray-100 dark:border-gray-800 last:border-b-0 animate-pulse">
      <div className="w-10 h-10 rounded-full bg-gray-200 dark:bg-gray-700 flex-shrink-0" />
      <div className="flex-1 flex flex-col gap-2">
        <div className="h-3.5 bg-gray-200 dark:bg-gray-700 rounded w-2/5" />
        <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-4/5" />
        <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-1/4" />
      </div>
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div
      className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl overflow-hidden"
      aria-busy="true"
      aria-label="Carregando notificacoes"
    >
      {Array.from({ length: 5 }).map((_, i) => (
        <NotificationSkeleton key={i} />
      ))}
    </div>
  )
}

// ---- Item individual ----------------------------------------------------

interface NotificationItemProps {
  notification: NotificationResponse
  onMarkAsRead: (id: string) => void
  isPending: boolean
}

function NotificationItem({ notification, onMarkAsRead, isPending }: NotificationItemProps) {
  const config = TYPE_CONFIG[notification.type]
  const isUnread = !notification.read

  function handleClick() {
    if (isUnread && !isPending) {
      onMarkAsRead(notification.id)
    }
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      handleClick()
    }
  }

  return (
    <article
      role="article"
      aria-label={`Notificacao ${isUnread ? 'nao lida' : 'lida'}: ${notification.title}`}
      className={cn(
        'flex items-start gap-3 px-5 py-4 border-b border-gray-100 dark:border-gray-800 last:border-b-0 transition-colors relative',
        isUnread
          ? 'bg-blue-50 dark:bg-blue-900/20 hover:bg-blue-100 dark:hover:bg-blue-900/30 cursor-pointer pl-6'
          : 'hover:bg-gray-50 dark:hover:bg-gray-800/50',
        isUnread && 'cursor-pointer',
      )}
      onClick={handleClick}
      onKeyDown={isUnread ? handleKeyDown : undefined}
      tabIndex={isUnread ? 0 : undefined}
    >
      {isUnread && (
        <span
          className="absolute left-2 top-5 w-2 h-2 rounded-full bg-blue-500"
          aria-hidden="true"
        />
      )}
      <NotificationIcon type={notification.type} />
      <div className="flex-1 min-w-0">
        <p className={cn('text-sm', isUnread ? 'font-semibold text-gray-900 dark:text-gray-100' : 'font-medium text-gray-800 dark:text-gray-200')}>
          {notification.title}
        </p>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 leading-relaxed">
          {notification.body}
        </p>
        <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
          <time dateTime={notification.createdAt} aria-label={config.label}>
            {formatRelativeDate(notification.createdAt)}
          </time>
        </p>
      </div>
    </article>
  )
}

// ---- Estado vazio -------------------------------------------------------

function EmptyState() {
  return (
    <div
      className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl flex flex-col items-center justify-center py-16 px-6 text-center"
      role="status"
      aria-live="polite"
    >
      <div className="w-12 h-12 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center mb-4" aria-hidden="true">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 01-3.46 0" />
        </svg>
      </div>
      <p className="font-semibold text-gray-700 dark:text-gray-300 mb-1">Sem notificacoes</p>
      <p className="text-sm text-gray-500 dark:text-gray-400 max-w-xs">
        Voce esta em dia! Quando houver novidades sobre sua conta, elas aparecerao aqui.
      </p>
    </div>
  )
}

// ---- Estado de erro -----------------------------------------------------

interface ErrorStateProps {
  onRetry: () => void
}

function ErrorState({ onRetry }: ErrorStateProps) {
  return (
    <div
      className="bg-white dark:bg-gray-900 border border-red-200 dark:border-red-800 rounded-xl flex flex-col items-center justify-center py-12 px-6 text-center"
      role="alert"
    >
      <p className="font-semibold text-red-600 dark:text-red-400 mb-1">
        Erro ao carregar notificacoes
      </p>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
        Nao foi possivel buscar suas notificacoes. Tente novamente.
      </p>
      <button
        onClick={onRetry}
        className="px-4 py-2 text-sm font-medium rounded-lg bg-primary-600 text-white hover:bg-primary-700 transition-colors"
      >
        Tentar novamente
      </button>
    </div>
  )
}

// ---- Pagina principal ---------------------------------------------------

export function NotificationsPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, refetch } = useNotifications(page)
  const { data: unreadData } = useUnreadCount()

  const markAsReadMutation = useMarkAsRead()
  const markAllAsReadMutation = useMarkAllAsRead()

  const unreadCount = unreadData?.unreadCount ?? 0
  const notifications = data?.content ?? []
  const hasMore = data !== undefined && data.page < data.totalPages - 1

  function handleMarkAsRead(id: string) {
    markAsReadMutation.mutate(id)
  }

  function handleMarkAllAsRead() {
    markAllAsReadMutation.mutate()
  }

  function handleLoadMore() {
    setPage((prev) => prev + 1)
  }

  return (
    <div className="flex flex-col gap-6 max-w-2xl">
      {/* Header */}
      <header className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2.5">
          <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100">
            Notificacoes
          </h1>
          {unreadCount > 0 && (
            <span
              className="inline-flex items-center justify-center px-2 py-0.5 text-xs font-bold rounded-full bg-red-500 text-white min-w-[1.375rem]"
              aria-label={`${unreadCount} notificacoes nao lidas`}
            >
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <button
            onClick={handleMarkAllAsRead}
            disabled={markAllAsReadMutation.isPending}
            className="px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors disabled:opacity-50"
            aria-label="Marcar todas as notificacoes como lidas"
          >
            {markAllAsReadMutation.isPending ? 'Marcando...' : 'Marcar todas como lidas'}
          </button>
        )}
      </header>

      {/* Conteudo */}
      {isLoading && <LoadingSkeleton />}

      {isError && <ErrorState onRetry={refetch} />}

      {!isLoading && !isError && notifications.length === 0 && <EmptyState />}

      {!isLoading && !isError && notifications.length > 0 && (
        <section aria-label="Lista de notificacoes">
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-3">
            {data?.totalElements ?? 0} notificacao{(data?.totalElements ?? 0) !== 1 ? 'es' : ''}
          </p>

          <div
            className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl overflow-hidden"
            aria-live="polite"
            aria-relevant="additions removals"
          >
            {notifications.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                onMarkAsRead={handleMarkAsRead}
                isPending={markAsReadMutation.isPending}
              />
            ))}
          </div>

          {hasMore && (
            <div className="mt-4 flex justify-center">
              <button
                onClick={handleLoadMore}
                className="px-5 py-2 text-sm font-medium rounded-lg border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              >
                Carregar mais
              </button>
            </div>
          )}
        </section>
      )}
    </div>
  )
}
