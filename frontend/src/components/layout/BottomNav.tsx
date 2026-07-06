import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useUnreadCount } from '@/features/notifications/useNotifications'

const items = [
  { to: '/dashboard', label: 'Inicio' },
  { to: '/carteira', label: 'Carteira' },
  { to: '/transferencias', label: 'Pix' },
  { to: '/extrato', label: 'Extrato' },
  { to: '/notificacoes', label: 'Alertas', showBadge: true },
]

export function BottomNav() {
  const { data: unreadData } = useUnreadCount()
  const unreadCount = unreadData?.unreadCount ?? 0

  return (
    <nav
      className="lg:hidden fixed bottom-0 left-0 right-0 bg-white dark:bg-gray-900 border-t border-gray-200 dark:border-gray-800 flex"
      aria-label="Navegacao inferior"
    >
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) => cn(
            'flex-1 flex flex-col items-center py-2 text-xs font-medium transition relative',
            isActive
              ? 'text-primary-600 dark:text-primary-400'
              : 'text-gray-500 dark:text-gray-400',
          )}
        >
          {item.showBadge && unreadCount > 0 && (
            <span
              className="absolute top-1 right-1/4 inline-flex items-center justify-center w-4 h-4 text-[10px] font-bold rounded-full bg-red-500 text-white"
              aria-label={`${unreadCount} notificacoes nao lidas`}
            >
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
