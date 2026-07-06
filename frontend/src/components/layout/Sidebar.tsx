import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useAuth } from '@/hooks/useAuth'
import { useUnreadCount } from '@/features/notifications/useNotifications'

const baseNavItems = [
  { to: '/dashboard', label: 'Inicio' },
  { to: '/carteira', label: 'Carteira' },
  { to: '/transferencias', label: 'Transferencias' },
  { to: '/extrato', label: 'Extrato' },
  { to: '/notificacoes', label: 'Notificacoes', showBadge: true },
  { to: '/perfil', label: 'Perfil' },
]

function UnreadBadge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span
      className="ml-auto inline-flex items-center justify-center px-1.5 py-0.5 text-xs font-bold rounded-full bg-red-500 text-white min-w-[1.25rem]"
      aria-label={`${count} notificacoes nao lidas`}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}

export function Sidebar() {
  const { user, logout } = useAuth()
  const { data: unreadData } = useUnreadCount()
  const unreadCount = unreadData?.unreadCount ?? 0

  return (
    <aside className="hidden lg:flex lg:flex-col lg:w-56 lg:min-h-screen bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-800 p-4">
      <div className="mb-8">
        <span className="text-lg font-bold text-primary-700 dark:text-primary-400">Nexus Bank</span>
        {user && (
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 truncate">{user.email}</p>
        )}
      </div>
      <nav className="flex-1 space-y-1" aria-label="Principal">
        {baseNavItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => cn(
              'flex items-center rounded-md px-3 py-2 text-sm font-medium transition',
              isActive
                ? 'bg-primary-50 text-primary-700 dark:bg-primary-950 dark:text-primary-400'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
            )}
          >
            {item.label}
            {item.showBadge && <UnreadBadge count={unreadCount} />}
          </NavLink>
        ))}
      </nav>
      <button
        onClick={logout}
        className="mt-4 w-full rounded-md px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950 transition"
      >
        Sair
      </button>
    </aside>
  )
}
