import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const items = [
  { to: '/dashboard', label: 'Inicio' },
  { to: '/carteira', label: 'Carteira' },
  { to: '/transferencias', label: 'Pix' },
  { to: '/extrato', label: 'Extrato' },
  { to: '/perfil', label: 'Perfil' },
]

export function BottomNav() {
  return (
    <nav
      className="lg:hidden fixed bottom-0 left-0 right-0 bg-white dark:bg-gray-900 border-t border-gray-200 dark:border-gray-800 flex"
      aria-label="Navegacao inferior"
    >
      {items.map(item => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) => cn(
            'flex-1 flex flex-col items-center py-2 text-xs font-medium transition',
            isActive
              ? 'text-primary-600 dark:text-primary-400'
              : 'text-gray-500 dark:text-gray-400',
          )}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
